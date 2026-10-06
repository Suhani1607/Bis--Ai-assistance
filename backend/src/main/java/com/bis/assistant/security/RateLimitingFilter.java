package com.bis.assistant.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final int AUTH_LIMIT_PER_MINUTE = 10;
    private static final int PUBLIC_LIMIT_PER_MINUTE = 60;
    private static final long ONE_MINUTE_MS = 60_000L;

    private final Map<String, Deque<Long>> requestCounts = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        int limit = resolveLimit(path);

        if (limit > 0) {
            String clientIp = extractClientIp(request);
            String bucketKey = clientIp + ":" + getEndpointCategory(path);

            if (!isAllowed(bucketKey, limit)) {
                log.warn("Rate limit exceeded for client {} on endpoint {}", clientIp, path);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.setHeader("Retry-After", "60");
                response.getWriter().write("""
                    {"status": 429, "error": "Too Many Requests", "message": "Rate limit exceeded. Please try again in 60 seconds."}
                    """);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private int resolveLimit(String path) {
        if (path.contains("/auth/login") || path.contains("/auth/register") || path.contains("/auth/refresh")) {
            return AUTH_LIMIT_PER_MINUTE;
        }
        if (path.contains("/standards/search") || path.contains("/standards/suggest")) {
            return PUBLIC_LIMIT_PER_MINUTE;
        }
        return -1; // No rate limit
    }

    private String getEndpointCategory(String path) {
        if (path.contains("/auth/")) return "auth";
        return "public";
    }

    private synchronized boolean isAllowed(String key, int limit) {
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = requestCounts.computeIfAbsent(key, k -> new ArrayDeque<>());

        // Evict timestamps older than 1 minute
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() > ONE_MINUTE_MS) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= limit) {
            return false;
        }

        timestamps.addLast(now);
        return true;
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }
}
