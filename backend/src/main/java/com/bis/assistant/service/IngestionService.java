package com.bis.assistant.service;

import com.bis.assistant.model.IngestionJob;
import com.bis.assistant.repository.IngestionJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {

    private final IngestionJobRepository jobRepo;
    private final WebClient.Builder webClientBuilder;
    private final IngestionJobRunner jobRunner;

    @Value("${rag.service-url:http://localhost:8000}")
    private String ragServiceUrl;

    // ── Trigger crawl job ─────────────────────────────────────

    @Transactional
    public Map<String, Object> triggerCrawl(String jobType) {
        IngestionJob job = IngestionJob.builder()
            .jobType(jobType)
            .status("PENDING")
            .triggeredBy("admin-api")
            .build();
        job = jobRepo.save(job);

        // Delegate to separate async runner bean
        jobRunner.runCrawlAsync(job.getId(), jobType);

        return Map.of(
            "jobId",   job.getId(),
            "type",    jobType,
            "status",  "PENDING",
            "message", "Ingestion job queued. Check /admin/ingestion/jobs for status."
        );
    }

    // ── Upload single PDF (streamed, up to 50 MB) ─────────────

    @Transactional
    public Map<String, Object> ingestUploadedFile(MultipartFile file, String docType, String isNumber) {
        if (file.isEmpty() || file.getSize() > 50L * 1024 * 1024) {
            throw new IllegalArgumentException("PDF uploads must be between 1 byte and 50 MB");
        }
        IngestionJob job = IngestionJob.builder()
            .jobType("SINGLE_DOC")
            .status("RUNNING")
            .triggeredBy("admin-upload")
            .startedAt(OffsetDateTime.now())
            .build();
        job = jobRepo.save(job);

        try {
            WebClient client = webClientBuilder.baseUrl(ragServiceUrl).build();

            // Stream file directly using file.getResource() without reading entire bytes to memory
            Resource fileResource = file.getResource();

            Map<?, ?> result = client.post()
                .uri("/rag/ingest/pdf")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(buildMultipart(fileResource, file.getOriginalFilename(), docType, isNumber, job.getId().toString()))
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofMinutes(10));

            int chunks = result != null && result.get("chunks_created") instanceof Number n
                ? n.intValue() : 0;

            job.setDocsIngested(1);
            job.setDocsFailed(0);
            job.setStatus("DONE");
            job.setFinishedAt(OffsetDateTime.now());
            jobRepo.save(job);

            return Map.of(
                "jobId",         job.getId(),
                "status",        "DONE",
                "chunksCreated", chunks,
                "filename",      file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.pdf"
            );
        } catch (Exception e) {
            log.error("PDF upload ingestion failed: {}", e.getMessage());
            job.setStatus("FAILED");
            job.setErrorLog(e.getMessage());
            job.setFinishedAt(OffsetDateTime.now());
            jobRepo.save(job);
            throw new RuntimeException("Ingestion failed: " + e.getMessage(), e);
        }
    }

    private MultiValueMap<String, Object> buildMultipart(
            Resource file, String filename, String docType, String isNumber, String docId) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file);
        body.add("title", filename != null ? filename.replace(".pdf", "") : "Uploaded Document");
        body.add("doc_type", docType);
        if (isNumber != null) body.add("is_number", isNumber);
        body.add("document_id", docId);
        return body;
    }

    // ── List jobs (with clamped pagination) ────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> listJobs(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));

        List<IngestionJob> jobs = jobRepo.findRecentJobs(PageRequest.of(safePage, safeSize));
        List<Map<String, Object>> items = jobs.stream().map(j -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",           j.getId());
            m.put("jobType",      j.getJobType());
            m.put("status",       j.getStatus());
            m.put("docsFound",    j.getDocsFound());
            m.put("docsIngested", j.getDocsIngested());
            m.put("docsFailed",   j.getDocsFailed());
            m.put("startedAt",    j.getStartedAt());
            m.put("finishedAt",   j.getFinishedAt());
            m.put("errorLog",     j.getErrorLog());
            return m;
        }).toList();

        return Map.of("jobs", items, "page", safePage, "size", safeSize);
    }
}
