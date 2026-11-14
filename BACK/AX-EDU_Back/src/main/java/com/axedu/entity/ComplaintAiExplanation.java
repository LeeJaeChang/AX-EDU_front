package com.axedu.entity;

import java.time.OffsetDateTime;

import com.axedu.entity.enums.AiRecommendation;
import com.axedu.entity.enums.AiStatus;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** STEP 1 AI 규정 설명 (complaint_ai_explanations). 재시도마다 1행, 화면에는 최신 성공 건 */
@Entity
@Table(name = "complaint_ai_explanations",
        uniqueConstraints = @UniqueConstraint(name = "uq_complaint_ai_explanations_complaint_id_attempt", columnNames = {"complaint_id", "attempt"}))
@Getter
@Setter
@NoArgsConstructor
public class ComplaintAiExplanation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_id", length = 32, nullable = false)
    private String complaintId;

    @Column(nullable = false)
    private short attempt = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiStatus status;

    @Column(length = 500)
    private String empathy;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(length = 500)
    private String alternative;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AiRecommendation recommendation;

    @Column(name = "model_name", length = 50, nullable = false)
    private String modelName;

    @JdbcTypeCode(SqlTypes.CHAR) // CHAR(64): SHA-256 hex
    @Column(name = "masked_input_hash", length = 64, nullable = false)
    private String maskedInputHash;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
