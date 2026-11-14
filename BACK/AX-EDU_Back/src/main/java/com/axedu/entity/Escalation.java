package com.axedu.entity;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.axedu.entity.enums.EscalationStage;
import com.axedu.entity.enums.EscalationStatus;

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

/** STEP 5 민원대응팀 도움 요청 (escalations). 민원당 1건 */
@Entity
@Table(name = "escalations", indexes = {
        @Index(name = "idx_escalations_school_stage_status_requested_at", columnList = "school_id, stage, status, requested_at"),
        @Index(name = "idx_escalations_assignee_id_status", columnList = "assignee_id, status"),
        @Index(name = "idx_escalations_requested_by", columnList = "requested_by")})
@Getter
@Setter
@NoArgsConstructor
public class Escalation {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "school_id", length = 32, nullable = false)
    private String schoolId;

    @Column(name = "complaint_id", length = 32, nullable = false, unique = true)
    private String complaintId;

    @Column(name = "requested_by", length = 32, nullable = false)
    private String requestedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reason_types", nullable = false)
    private List<String> reasonTypes = new ArrayList<>();

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EscalationStage stage = EscalationStage.RESPONSE_TEAM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EscalationStatus status = EscalationStatus.REQUESTED;

    @Column(name = "assignee_id", length = 32)
    private String assigneeId;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (requestedAt == null) {
            requestedAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
