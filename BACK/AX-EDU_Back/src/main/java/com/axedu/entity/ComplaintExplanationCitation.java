package com.axedu.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** AI 설명이 인용한 규정 조항 (complaint_explanation_citations) */
@Entity
@Table(name = "complaint_explanation_citations")
@IdClass(ComplaintExplanationCitation.Pk.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintExplanationCitation {

    @Id
    @Column(name = "explanation_id")
    private Long explanationId;

    @Id
    @Column(name = "regulation_chunk_id", length = 32)
    private String regulationChunkId;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private Long explanationId;
        private String regulationChunkId;
    }
}
