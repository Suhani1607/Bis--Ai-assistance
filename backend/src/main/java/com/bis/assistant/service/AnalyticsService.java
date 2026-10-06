package com.bis.assistant.service;

import com.bis.assistant.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final JdbcTemplate jdbc;
    private final FeedbackRepository feedbackRepo;

    // ── Query analytics ───────────────────────────────────────

    public Map<String, Object> getQueryStats(int days) {
        Map<String, Object> stats = new LinkedHashMap<>();

        // Total queries in period
        Integer total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM query_analytics WHERE created_at >= NOW() - INTERVAL '%d days'"
                .formatted(days),
            Integer.class);
        stats.put("totalQueries", total);

        // Breakdown by intent
        List<Map<String, Object>> byIntent = jdbc.queryForList(
            """
            SELECT detected_intent AS intent, COUNT(*) AS count
            FROM query_analytics
            WHERE created_at >= NOW() - INTERVAL '%d days'
            GROUP BY detected_intent
            ORDER BY count DESC
            """.formatted(days));
        stats.put("byIntent", byIntent);

        // Breakdown by language
        List<Map<String, Object>> byLang = jdbc.queryForList(
            """
            SELECT detected_lang AS lang, COUNT(*) AS count
            FROM query_analytics
            WHERE created_at >= NOW() - INTERVAL '%d days'
            GROUP BY detected_lang
            ORDER BY count DESC
            """.formatted(days));
        stats.put("byLanguage", byLang);

        // Average latency
        Double avgLatency = jdbc.queryForObject(
            "SELECT AVG(latency_ms) FROM query_analytics WHERE created_at >= NOW() - INTERVAL '%d days'"
                .formatted(days),
            Double.class);
        stats.put("avgLatencyMs", avgLatency != null ? Math.round(avgLatency) : 0);

        // Fallback rate
        Integer fallbacks = jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM query_analytics
            WHERE was_fallback = TRUE AND created_at >= NOW() - INTERVAL '%d days'
            """.formatted(days),
            Integer.class);
        stats.put("fallbackCount", fallbacks);
        stats.put("fallbackRate",
            (total != null && total > 0 && fallbacks != null)
                ? String.format("%.1f%%", (fallbacks * 100.0) / total) : "0%");

        // Daily volume for chart (last N days)
        List<Map<String, Object>> daily = jdbc.queryForList(
            """
            SELECT DATE(created_at) AS date, COUNT(*) AS count
            FROM query_analytics
            WHERE created_at >= NOW() - INTERVAL '%d days'
            GROUP BY DATE(created_at)
            ORDER BY date ASC
            """.formatted(days));
        stats.put("dailyVolume", daily);

        // Top 10 most-asked products
        List<Map<String, Object>> topProducts = jdbc.queryForList(
            """
            SELECT detected_product AS product, COUNT(*) AS count
            FROM query_analytics
            WHERE detected_product IS NOT NULL
              AND created_at >= NOW() - INTERVAL '%d days'
            GROUP BY detected_product
            ORDER BY count DESC
            LIMIT 10
            """.formatted(days));
        stats.put("topProducts", topProducts);

        stats.put("periodDays", days);
        return stats;
    }

    // ── Feedback summary ─────────────────────────────────────

    public Map<String, Object> getFeedbackStats(int days) {
        Map<String, Object> stats = new LinkedHashMap<>();

        Integer totalPositive = jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM feedback f
            JOIN messages m ON f.message_id = m.id
            WHERE f.rating = 1 AND m.created_at >= NOW() - INTERVAL '%d days'
            """.formatted(days),
            Integer.class);

        Integer totalNegative = jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM feedback f
            JOIN messages m ON f.message_id = m.id
            WHERE f.rating = -1 AND m.created_at >= NOW() - INTERVAL '%d days'
            """.formatted(days),
            Integer.class);

        int pos = totalPositive != null ? totalPositive : 0;
        int neg = totalNegative != null ? totalNegative : 0;
        int total = pos + neg;

        stats.put("positiveCount", pos);
        stats.put("negativeCount", neg);
        stats.put("totalFeedback", total);
        stats.put("satisfactionRate",
            total > 0 ? String.format("%.1f%%", (pos * 100.0) / total) : "N/A");

        // Recent negative feedback with comments (for review)
        List<Map<String, Object>> negativeComments = jdbc.queryForList(
            """
            SELECT f.comment, m.content AS question, f.created_at
            FROM feedback f
            JOIN messages m ON f.message_id = m.id
            WHERE f.rating = -1 AND f.comment IS NOT NULL
              AND m.created_at >= NOW() - INTERVAL '%d days'
            ORDER BY f.created_at DESC
            LIMIT 20
            """.formatted(days));
        stats.put("recentNegativeComments", negativeComments);

        stats.put("periodDays", days);
        return stats;
    }
}
