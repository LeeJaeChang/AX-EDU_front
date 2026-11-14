package com.axedu.entity;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.axedu.entity.enums.CardSource;
import com.axedu.entity.enums.UrgencyLevel;
import com.axedu.entity.value.ParentEmotion;
import com.axedu.entity.value.CardSuggestion;
import com.axedu.entity.value.SituationItem;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 요약 카드 버전 (summary_cards). 수정할 때마다 새 행, 기존 행은 고치지 않는다 (confirmed_at 제외) */
@Entity
@Table(name = "summary_cards",
        uniqueConstraints = @UniqueConstraint(name = "uq_summary_cards_complaint_id_version", columnNames = {"complaint_id", "version"}),
        indexes = @Index(name = "idx_summary_cards_created_by", columnList = "created_by"))
@Getter
@Setter
@NoArgsConstructor
public class SummaryCard {

    public static final String DEFAULT_ATTRIBUTION = "학부모님 설명에 따르면";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_id", length = 32, nullable = false)
    private String complaintId;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardSource source;

    @Column(name = "revision_instruction", length = 1000)
    private String revisionInstruction;

    @Column(nullable = false)
    private short grade;

    @Column(name = "class_no", nullable = false)
    private short classNo;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> tags = new ArrayList<>();

    @Column(length = 50, nullable = false)
    private String attribution = DEFAULT_ATTRIBUTION;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<SituationItem> situation = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> concerns = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> requests = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_methods", nullable = false)
    private List<String> preferredMethods = new ArrayList<>();

    @Column(name = "preferred_from")
    private LocalTime preferredFrom;

    @Column(name = "preferred_to")
    private LocalTime preferredTo;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<CardSuggestion> suggestions;

    /** 대응 시급도 (학부모·교사 모두 본다) */
    @Enumerated(EnumType.STRING)
    @Column(name = "urgency_level", length = 8, nullable = false)
    private UrgencyLevel urgencyLevel = UrgencyLevel.NORMAL;

    /** 예: '학생 안전·건강 관련'. URGENT 가 아니면 null */
    @Column(name = "urgency_reason", length = 100)
    private String urgencyReason;

    /** 학부모 감정 상태 (교사·민원대응팀만 본다). 학부모가 고칠 수 없고 수정 버전에 그대로 이어진다 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parent_emotion")
    private ParentEmotion parentEmotion;

    @Column(name = "created_by", length = 32, nullable = false)
    private String createdBy;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
