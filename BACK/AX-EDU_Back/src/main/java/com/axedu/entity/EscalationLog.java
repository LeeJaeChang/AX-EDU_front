package com.axedu.entity;

import java.time.OffsetDateTime;

import com.axedu.entity.enums.EscalationAction;
import com.axedu.entity.enums.EscalationStage;
import com.axedu.entity.enums.EscalationStatus;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 이관 처리 이력 (escalation_logs) */
@Entity
@Table(name = "escalation_logs", indexes = {
        @Index(name = "idx_escalation_logs_escalation_id_created_at", columnList = "escalation_id, created_at"),
        @Index(name = "idx_escalation_logs_actor_id", columnList = "actor_id"),
        @Index(name = "idx_escalation_logs_assignee_id", columnList = "assignee_id")})
@Getter
@Setter
@NoArgsConstructor
public class EscalationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "escalation_id", length = 32, nullable = false)
    private String escalationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EscalationAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_stage", length = 20)
    private EscalationStage fromStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_stage", length = 20)
    private EscalationStage toStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private EscalationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 20)
    private EscalationStatus toStatus;

    @Column(name = "assignee_id", length = 32)
    private String assigneeId;

    @Column(length = 1000)
    private String note;

    @Column(name = "actor_id", length = 32, nullable = false)
    private String actorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
