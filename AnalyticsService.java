package com.interviewos.service;

import com.interviewos.model.Submission;
import com.interviewos.model.User;
import com.interviewos.repository.SubmissionRepository;
import com.interviewos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Replaces analyticsController.js which used MongoDB aggregation pipelines.
 *
 * Spring Data MongoDB's Aggregation API mirrors the original pipeline:
 *   $match → $group → $project → $sort
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final MongoTemplate mongoTemplate;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;

    public Map<String, Object> getUserAnalytics(String userId) {
        // Topic-wise accuracy (mirrors MongoDB $group in analyticsController.js)
        Aggregation topicAgg = Aggregation.newAggregation(
            Aggregation.match(Criteria.where("userId").is(userId)),
            Aggregation.group("questionTopic")
                .count().as("total")
                .sum(ConditionalOperators
                    .when(Criteria.where("status").is("ACCEPTED"))
                    .then(1).otherwise(0))
                .as("passed"),
            Aggregation.project("total", "passed")
                .and("_id").as("topic")
                .andExpression("passed * 100.0 / total").as("accuracy"),
            Aggregation.sort(Sort.by(Sort.Direction.DESC, "total"))
        );

        List<Map> topicStats = mongoTemplate.aggregate(
            topicAgg, "submissions", Map.class).getMappedResults();

        // Recent submissions
        List<Submission> recent = submissionRepository
            .findTop10ByUserIdOrderBySubmittedAtDesc(userId);

        // User profile
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topicStats",   topicStats);
        result.put("recentSubmissions", recent);
        result.put("currentLevel", user.getCurrentLevel());
        result.put("eloRating",    user.getEloRating());
        result.put("streakDays",   user.getStreakDays());
        result.put("badges",       user.getBadges());
        return result;
    }

    public List<Map> getLeaderboard() {
        // Top 50 users by Elo
        Aggregation leaderboardAgg = Aggregation.newAggregation(
            Aggregation.project("name", "eloRating", "currentLevel", "badges", "streakDays"),
            Aggregation.sort(Sort.by(Sort.Direction.DESC, "eloRating")),
            Aggregation.limit(50)
        );
        return mongoTemplate.aggregate(leaderboardAgg, "users", Map.class).getMappedResults();
    }
}
