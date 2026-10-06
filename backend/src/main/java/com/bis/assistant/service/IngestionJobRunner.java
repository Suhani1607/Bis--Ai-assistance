package com.bis.assistant.service;

import com.bis.assistant.model.IngestionJob;
import com.bis.assistant.repository.IngestionJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionJobRunner {
    private final IngestionJobRepository jobRepo;
    private final WebClient.Builder webClientBuilder;

    @Value("${rag.service-url:http://localhost:8000}")
    private String ragServiceUrl;

    @Async
    public void runCrawlAsync(UUID jobId, String jobType) {
        IngestionJob job = jobRepo.findById(jobId).orElse(null);
        if (job == null) return;

        job.setStatus("RUNNING");
        job.setStartedAt(OffsetDateTime.now());
        jobRepo.save(job);

        try {
            WebClient client = webClientBuilder.baseUrl(ragServiceUrl).build();
            Map<?, ?> triggerResponse = client.post()
                .uri("/rag/crawl")
                .bodyValue(Map.of("jobId", jobId.toString(), "jobType", jobType))
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(30));

            String status = triggerResponse != null && triggerResponse.get("status") != null
                ? triggerResponse.get("status").toString() : "RUNNING";

            // If it returned DONE immediately
            if ("DONE".equalsIgnoreCase(status)) {
                updateJobFinished(job, triggerResponse, "DONE", null);
                return;
            }

            // Otherwise poll status endpoint until finished (up to 30 mins)
            long startTime = System.currentTimeMillis();
            long maxWaitMs = Duration.ofMinutes(30).toMillis();

            while (System.currentTimeMillis() - startTime < maxWaitMs) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }

                Map<?, ?> statusResponse = client.get()
                    .uri("/rag/crawl/{id}/status", jobId.toString())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(10));

                if (statusResponse != null) {
                    job.setDocsFound(number(statusResponse, "totalFound"));
                    job.setDocsIngested(number(statusResponse, "docsIngested"));
                    job.setDocsFailed(number(statusResponse, "docsFailed"));
                    String currentStatus = statusResponse.get("status") != null
                        ? statusResponse.get("status").toString() : "RUNNING";

                    if ("DONE".equalsIgnoreCase(currentStatus)) {
                        updateJobFinished(job, statusResponse, "DONE", null);
                        return;
                    } else if ("FAILED".equalsIgnoreCase(currentStatus)) {
                        String err = statusResponse.get("error") != null
                            ? statusResponse.get("error").toString() : "Crawl failed";
                        updateJobFinished(job, statusResponse, "FAILED", err);
                        return;
                    }
                    jobRepo.save(job);
                }
            }

            // Timed out
            updateJobFinished(job, null, "FAILED", "Crawl operation timed out after 30 minutes");

        } catch (Exception e) {
            log.error("Crawl job {} failed", jobId, e);
            updateJobFinished(job, null, "FAILED", e.getMessage());
        }
    }

    private void updateJobFinished(IngestionJob job, Map<?, ?> result, String status, String error) {
        if (result != null) {
            job.setDocsFound(number(result, "totalFound"));
            job.setDocsIngested(number(result, "docsIngested"));
            job.setDocsFailed(number(result, "docsFailed"));
        }
        job.setStatus(status);
        if (error != null) {
            job.setErrorLog(error);
        }
        job.setFinishedAt(OffsetDateTime.now());
        jobRepo.save(job);
    }

    private int number(Map<?, ?> result, String key) {
        return result != null && result.get(key) instanceof Number value ? value.intValue() : 0;
    }
}
