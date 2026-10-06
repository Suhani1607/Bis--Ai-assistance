package com.bis.assistant.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();

        manager.setCaches(List.of(
            // IS catalogue text search — expires quickly (standards don't change often,
            // but new IS can be published; 5 min is a safe window)
            build("standards-search", 500, 5),

            // Single IS detail — longer TTL since specific IS rarely changes
            build("standards-detail", 200, 10),

            // Scheme list — very stable, cache for 30 min
            build("standards-schemes", 10, 30)
        ));

        return manager;
    }

    private CaffeineCache build(String name, int maxSize, int ttlMinutes) {
        return new CaffeineCache(
            name,
            Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .recordStats()          // visible via /actuator/metrics/cache.*
                .build()
        );
    }
}
