package com.interviewos.service;

import com.interviewos.dto.JudgeRequest;
import com.interviewos.dto.JudgeResponse;
import com.interviewos.dto.RunResult;
import com.interviewos.model.Question;
import com.interviewos.model.Submission;
import com.interviewos.model.User;
import com.interviewos.repository.QuestionRepository;
import com.interviewos.repository.SubmissionRepository;
import com.interviewos.repository.UserRepository;
import com.interviewos.util.AdaptiveEngine;
import com.interviewos.util.CodeRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the combined judge + adaptiveEngine logic from the original.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeService {

    private final CodeRunner codeRunner;
    private final AdaptiveEngine adaptiveEngine;
    private final QuestionRepository questionRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;

    private static final long DEFAULT_TIME_LIMIT_MS = 5000;

    public JudgeResponse submit(String userId, JudgeRequest request) {
        Question question = questionRepository.findById(request.getQuestionId())
            .orElseThrow(() -> new RuntimeException("Question not found"));

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        // Run against all test cases
        List<JudgeResponse.TestResult> results = runAllTests(request, question);

        int passed = (int) results.stream().filter(JudgeResponse.TestResult::isPassed).count();
        boolean allPassed = passed == results.size();
        long maxExecTime = results.stream().mapToLong(JudgeResponse.TestResult::getExecTimeMs).max().orElse(0);

        // Adaptive engine — replaces utils/adaptiveEngine.js
        int prevAttempts = submissionRepository
            .countByUserIdAndQuestionId(userId, request.getQuestionId());

        AdaptiveEngine.AdaptiveResult adaptive = adaptiveEngine.evaluate(
            user.getCurrentLevel(),
            user.getEloRating(),
            allPassed,
            prevAttempts + 1,
            maxExecTime,
            DEFAULT_TIME_LIMIT_MS,
            user.getStreakDays()
        );

        // Persist submission
        Submission sub = new Submission();
        sub.setUserId(userId);
        sub.setQuestionId(request.getQuestionId());
        sub.setQuestionTopic(question.getTopic());
        sub.setDifficulty(question.getDifficulty());
        sub.setCode(request.getCode());
        sub.setLanguage(request.getLanguage());
        sub.setStatus(allPassed ? "ACCEPTED" : "WRONG_ANSWER");
        sub.setTestsPassed(passed);
        sub.setTotalTests(results.size());
        sub.setExecutionTimeMs(maxExecTime);
        sub.setFeedbackMessage(adaptive.feedbackMessage());
        sub.setNewLevel(adaptive.newLevel());
        sub.setSubmittedAt(Instant.now());
        submissionRepository.save(sub);

        // Update user level + elo
        user.setCurrentLevel(adaptive.newLevel());
        user.setEloRating(adaptive.newElo());
        user.setLastActiveAt(Instant.now());
        for (String badge : adaptive.badgesEarned()) {
            if (!user.getBadges().contains(badge)) {
                user.getBadges().add(badge);
            }
        }
        userRepository.save(user);

        return JudgeResponse.builder()
            .status(sub.getStatus())
            .testsPassed(passed)
            .totalTests(results.size())
            .results(results)
            .executionTimeMs(maxExecTime)
            .feedbackMessage(adaptive.feedbackMessage())
            .newLevel(adaptive.newLevel())
            .newElo(adaptive.newElo())
            .badgesEarned(List.of(adaptive.badgesEarned()))
            .build();
    }

    public JudgeResponse run(String userId, JudgeRequest request) {
        Question question = questionRepository.findById(request.getQuestionId())
            .orElseThrow(() -> new RuntimeException("Question not found"));

        // Only run visible test cases for "Run" (not submit)
        List<JudgeResponse.TestResult> results = runVisibleTests(request, question);
        int passed = (int) results.stream().filter(JudgeResponse.TestResult::isPassed).count();

        return JudgeResponse.builder()
            .status(passed == results.size() ? "OK" : "WRONG_ANSWER")
            .testsPassed(passed)
            .totalTests(results.size())
            .results(results)
            .build();
    }

    public Submission review(String userId, String submissionId) {
        return submissionRepository.findByIdAndUserId(submissionId, userId)
            .orElseThrow(() -> new RuntimeException("Submission not found"));
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private List<JudgeResponse.TestResult> runAllTests(JudgeRequest req, Question question) {
        List<JudgeResponse.TestResult> results = new ArrayList<>();
        for (Question.TestCase tc : question.getTestCases()) {
            results.add(runSingle(req, tc));
        }
        return results;
    }

    private List<JudgeResponse.TestResult> runVisibleTests(JudgeRequest req, Question question) {
        List<JudgeResponse.TestResult> results = new ArrayList<>();
        for (Question.TestCase tc : question.getTestCases()) {
            if (!tc.isHidden()) results.add(runSingle(req, tc));
        }
        return results;
    }

    private JudgeResponse.TestResult runSingle(JudgeRequest req, Question.TestCase tc) {
        try {
            RunResult run = switch (req.getLanguage().toLowerCase()) {
                case "javascript" -> codeRunner.runJavaScript(req.getCode(), tc.getInput(), tc.getExpectedOutput());
                case "python"     -> codeRunner.runPython(req.getCode(), tc.getInput(), tc.getExpectedOutput());
                case "java"       -> codeRunner.runJava(req.getCode(), tc.getInput(), tc.getExpectedOutput());
                default           -> throw new IllegalArgumentException("Unsupported language: " + req.getLanguage());
            };

            boolean passed = run.getExitCode() == 0 && "OK".equals(run.getStatus());
            return JudgeResponse.TestResult.builder()
                .passed(passed)
                .execTimeMs(run.getExecutionTimeMs())
                .stderr(run.getStderr())
                .hidden(tc.isHidden())
                .build();

        } catch (Exception e) {
            log.error("Code execution error: {}", e.getMessage());
            return JudgeResponse.TestResult.builder()
                .passed(false)
                .stderr(e.getMessage())
                .build();
        }
    }
}
