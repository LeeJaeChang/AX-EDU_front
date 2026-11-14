package com.axedu.entity;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.axedu.entity.enums.RegulationStatus;
import com.axedu.entity.enums.SourceType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 학칙·가정통신문·법령 문서 (regulations). school_id 가 NULL 이면 전국 공통 */
@Entity
@Table(name = "regulations", indexes = {
        @Index(name = "idx_regulations_school_id_status_source_type", columnList = "school_id, status, source_type"),
        @Index(name = "idx_regulations_file_id", columnList = "file_id"),
        @Index(name = "idx_regulations_uploaded_by", columnList = "uploaded_by")})
@Getter
@Setter
@NoArgsConstructor
public class Regulation {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "school_id", length = 32)
    private String schoolId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private SourceType sourceType;

    @Column(length = 200, nullable = false)
    private String title;

    @Column(name = "file_id", length = 32)
    private String fileId;

    @Column(name = "document_url", length = 500)
    private String documentUrl;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegulationStatus status = RegulationStatus.PROCESSING;

    @Column(name = "uploaded_by", length = 32)
    private String uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
