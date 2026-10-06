package com.bis.assistant.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ingestion_jobs")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class IngestionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** FULL_CRAWL | INCREMENTAL | SINGLE_DOC */
    @Column(name = "job_type", nullable = false, length = 100)
    private String jobType;

    /** PENDING | RUNNING | DONE | FAILED */
    @Column(nullable = false, length = 50)
    private String status = "PENDING";

    @Column(name = "triggered_by")
    private String triggeredBy;

    @Column(name = "docs_found")
    private Integer docsFound = 0;

    @Column(name = "docs_ingested")
    private Integer docsIngested = 0;

    @Column(name = "docs_failed")
    private Integer docsFailed = 0;

    @Column(name = "error_log", columnDefinition = "TEXT")
    private String errorLog;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
