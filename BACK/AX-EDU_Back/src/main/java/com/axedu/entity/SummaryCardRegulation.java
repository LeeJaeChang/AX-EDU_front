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

/** 카드에 붙은 관련 규정 (summary_card_regulations) */
@Entity
@Table(name = "summary_card_regulations")
@IdClass(SummaryCardRegulation.Pk.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SummaryCardRegulation {

    @Id
    @Column(name = "card_id")
    private Long cardId;

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
        private Long cardId;
        private String regulationChunkId;
    }
}
