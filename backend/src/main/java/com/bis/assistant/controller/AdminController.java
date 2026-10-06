package com.bis.assistant.controller;

import com.bis.assistant.service.IngestionService;
import com.bis.assistant.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.Map;

/**
 * Admin-only endpoints (ROLE_ADMIN or BIS_OFFICER required).
 *
 * POST /admin/ingestion/crawl           — trigger full BIS portal crawl
 * POST /admin/ingestion/upload          — upload a PDF for ingestion
 * GET  /admin/ingestion/jobs            — list ingestion job history
 * GET  /admin/analytics/queries         — query analytics dashboard data
 * GET  /admin/analytics/feedback        — feedback summary
 */
@RestController
@RequestMapping("/admin")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
@Validated
public class AdminController {

    private final IngestionService ingestionService;
    private final AnalyticsService analyticsService;

    // ── Ingestion ────────────────────────────────────────────

    @PostMapping("/ingestion/crawl")
    public ResponseEntity<Map<String, Object>> triggerCrawl(
            @RequestParam(defaultValue = "INCREMENTAL") String jobType) {
        Map<String, Object> job = ingestionService.triggerCrawl(jobType);
        return ResponseEntity.accepted().body(job);
    }

    @PostMapping("/ingestion/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam String docType,
            @RequestParam(required = false) String isNumber) {
        Map<String, Object> result = ingestionService.ingestUploadedFile(file, docType, isNumber);
        return ResponseEntity.accepted().body(result);
    }

    @GetMapping("/ingestion/jobs")
    public ResponseEntity<Map<String, Object>> listJobs(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ingestionService.listJobs(page, size));
    }

    // ── Analytics ────────────────────────────────────────────

    @GetMapping("/analytics/queries")
    public ResponseEntity<Map<String, Object>> queryAnalytics(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(analyticsService.getQueryStats(days));
    }

    @GetMapping("/analytics/feedback")
    public ResponseEntity<Map<String, Object>> feedbackSummary(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(analyticsService.getFeedbackStats(days));
    }
}
