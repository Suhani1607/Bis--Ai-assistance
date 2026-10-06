package com.bis.assistant.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "is_catalogue")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class IsCatalogue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "is_number", unique = true, nullable = false, length = 100)
    private String isNumber;

    @Column(nullable = false, length = 1000)
    private String title;

    @Column(name = "title_hi", length = 1000)
    private String titleHi;

    private Short year;

    @Column(length = 255)
    private String division;

    @Column(name = "product_scope", columnDefinition = "TEXT")
    private String productScope;

    /** SCHEME_I | CRS | FMCS | HALLMARKING | NONE */
    @Column(name = "cert_scheme", length = 100)
    private String certScheme;

    @Column(name = "is_mandatory")
    private boolean mandatory = false;

    /** CURRENT | SUPERSEDED | WITHDRAWN */
    @Column(length = 50)
    private String status = "CURRENT";

    @Column(length = 100)
    private String replaces;

    @Column(name = "replaced_by", length = 100)
    private String replacedBy;

    @Column(name = "document_id")
    private UUID documentId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
