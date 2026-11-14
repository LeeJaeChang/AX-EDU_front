package com.axedu.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 문서를 조항 단위로 나눈 것 (regulation_chunks). API 의 Regulation 한 건 */
@Entity
@Table(name = "regulation_chunks", indexes = @Index(name = "idx_regulation_chunks_regulation_id_sort_order", columnList = "regulation_id, sort_order"))
@Getter
@Setter
@NoArgsConstructor
public class RegulationChunk {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "regulation_id", length = 32, nullable = false)
    private String regulationId;

    @Column(name = "article_label", length = 100, nullable = false)
    private String articleLabel;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "vector_id", length = 100)
    private String vectorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
