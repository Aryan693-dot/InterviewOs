package com.interviewos.util;

import org.springframework.stereotype.Component;

/**
 * Replaces utils/adaptiveEngine.js
 *
 * Rule-based difficulty adaptation — no AI/LLM required.
 *
 * Algorithm:
 *   - Weighted score = (pass ? 100 : 0) - penalty(attempts) - penalty(time)
 *   - Score >= 70  → promote level
 *   - Score <= 30  → demote level
 *   - 30 < score < 70 → stay
 *
 * Elo update uses standard K-factor formula (same as chess).
 */
@Component
public class AdaptiveEngine {

    private static final int K_FACTOR = 32;

    public record AdaptiveResult(
        String newLevel,
        int    newElo,
        String feedbackMessage,
        String[] badgesEarned
    ) {}

    /**
     * Called after each submission to decide next difficulty level.
     */
    public AdaptiveResult evaluate(
            String currentLevel,
            int currentElo,
            boolean passed,
            int attempts,
            long executionTimeMs,
            long timeLimitMs,
            int streakDays) {

        int score = computeScore(passed, attempts, executionTimeMs, timeLimitMs);
        String newLevel = adjustLevel(currentLevel, score);
        int newElo      = updateElo(currentElo, passed);
        String feedback = buildFeedback(passed, score, attempts, executionTimeMs, timeLimitMs);
        String[] badges = checkBadges(passed, streakDays, newElo, executionTimeMs);

        return new AdaptiveResult(newLevel, newElo, feedback, badges);
    }

    // ─── Private ─────────────────────────────────────────────────────────────

    private int computeScore(boolean passed, int attempts, long execMs, long limitMs) {
        if (!passed) return 0;
        int base = 100;
        base -= Math.min(40, (attempts - 1) * 10); // -10 per extra attempt, max -40
        double timeRatio = (double) execMs / limitMs;
        if (timeRatio > 0.8) base -= 20;
        else if (timeRatio > 0.5) base -= 10;
        return Math.max(0, base);
    }

    private String adjustLevel(String current, int score) {
        return switch (current) {
            case "Easy"   -> score >= 70 ? "Medium" : "Easy";
            case "Medium" -> score >= 70 ? "Hard"   : (score <= 30 ? "Easy"   : "Medium");
            case "Hard"   -> score <= 30 ? "Medium" : "Hard";
            default       -> "Easy";
        };
    }

    private int updateElo(int currentElo, boolean won) {
        // Assume opponent Elo = 1200 (baseline question difficulty)
        double expected = 1.0 / (1.0 + Math.pow(10, (1200 - currentElo) / 400.0));
        double actual   = won ? 1.0 : 0.0;
        return currentElo + (int) Math.round(K_FACTOR * (actual - expected));
    }

    private String buildFeedback(boolean passed, int score, int attempts,
                                  long execMs, long limitMs) {
        if (!passed) {
            if (execMs >= limitMs) return "⏱ Time Limit Exceeded — check for O(n²) loops.";
            if (attempts > 3)      return "💡 Multiple attempts — review the problem constraints carefully.";
            return "❌ Wrong answer — trace through your logic with a simple example.";
        }
        if (score >= 90) return "🏆 Perfect! Solved on the first try with great speed.";
        if (score >= 70) return "✅ Correct! Consider optimising your time complexity.";
        return "✅ Correct — but took several attempts. Review similar patterns.";
    }

    private String[] checkBadges(boolean passed, int streakDays, int elo, long execMs) {
        var earned = new java.util.ArrayList<String>();
        if (passed && execMs < 100)   earned.add("speed-demon");
        if (elo >= 1500)               earned.add("elo-master");
        if (streakDays >= 7)           earned.add("week-warrior");
        if (streakDays >= 30)          earned.add("month-champion");
        return earned.toArray(new String[0]);
    }
}
