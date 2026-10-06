package com.bis.assistant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Logs every RAG query to the query_analytics table.
 * All writes are async and best-effort — failures must never break chat.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QueryAnalyticsService {

    private final JdbcTemplate jdbc;

    /**
     * Immutable record for a single query log entry.
     */
    public record QueryLog(
        String  queryText,
        String  detectedIntent,
        String  detectedLang,
        String  detectedProduct,   // nullable
        int     resultCount,
        float   topScore,
        int     latencyMs,
        boolean wasFallback,
        UUID    userId             // nullable for anonymous
    ) {}

    /**
     * Async fire-and-forget insert.
     * Never throws — catch-all prevents it from surfacing to callers.
     */
    @Async
    public void logQuery(QueryLog entry) {
        try {
            jdbc.update("""
                INSERT INTO query_analytics
                  (user_id, query_text, detected_intent, detected_lang,
                   detected_product, result_count, top_score, latency_ms, was_fallback)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                entry.userId(),
                truncate(entry.queryText(), 2000),
                entry.detectedIntent(),
                entry.detectedLang(),
                entry.detectedProduct(),
                entry.resultCount(),
                entry.topScore(),
                entry.latencyMs(),
                entry.wasFallback()
            );
        } catch (Exception e) {
            // Analytics must NEVER break the chat flow
            log.warn("Failed to log query analytics: {}", e.getMessage());
        }
    }

    // Avoid storing huge queries (safety valve)
    private String truncate(String text, int max) {
        if (text == null) return null;
        return text.length() > max ? text.substring(0, max) : text;
    }
}
