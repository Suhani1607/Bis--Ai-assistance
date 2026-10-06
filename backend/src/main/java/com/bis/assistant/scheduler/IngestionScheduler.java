package com.bis.assistant.scheduler;

import com.bis.assistant.service.IngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * Scheduled BIS document ingestion jobs.
 *
 * Weekly incremental crawl — every Sunday at 02:00 AM IST.
 * This keeps the knowledge base up to date with newly published IS documents,
 * revised scheme manuals, and updated hallmarking regulations.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IngestionScheduler {

    private final IngestionService ingestionService;

    /**
     * Weekly incremental crawl.
     * Cron: second minute hour day-of-month month day-of-week
     * "0 0 2 * * SUN" = every Sunday at 02:00:00
     *
     * Set INGESTION_CRON env var to override (e.g. "0 0 2 * * *" for daily).
     */
    @Scheduled(cron = "${ingestion.cron:0 0 2 * * SUN}")
    public void weeklyIncrementalCrawl() {
        log.info("Scheduled ingestion starting at {}", OffsetDateTime.now());
        try {
            ingestionService.triggerCrawl("INCREMENTAL");
            log.info("Scheduled ingestion job queued successfully");
        } catch (Exception e) {
            log.error("Scheduled ingestion failed to start: {}", e.getMessage(), e);
        }
    }

    /**
     * Monthly full crawl — first day of every month at 03:00 AM.
     * Re-ingests all seed documents to pick up any structural changes.
     */
    @Scheduled(cron = "${ingestion.full-crawl-cron:0 0 3 1 * *}")
    public void monthlyFullCrawl() {
        log.info("Monthly full crawl starting at {}", OffsetDateTime.now());
        try {
            ingestionService.triggerCrawl("FULL_CRAWL");
            log.info("Monthly full crawl job queued successfully");
        } catch (Exception e) {
            log.error("Monthly full crawl failed to start: {}", e.getMessage(), e);
        }
    }
}
