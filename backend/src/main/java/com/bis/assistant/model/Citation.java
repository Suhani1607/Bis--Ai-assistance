package com.bis.assistant.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "citations")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Citation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @Column(name = "chunk_id", nullable = false)
    private String chunkId;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "is_number", length = 50)
    private String isNumber;

    @Column(name = "clause_ref")
    private String clauseRef;

    @Column(name = "section_title")
    private String sectionTitle;

    @Column(columnDefinition = "TEXT")
    private String excerpt;

    @Column(name = "relevance_score")
    private Float relevanceScore;

    @Column(name = "sort_order")
    private Short sortOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
