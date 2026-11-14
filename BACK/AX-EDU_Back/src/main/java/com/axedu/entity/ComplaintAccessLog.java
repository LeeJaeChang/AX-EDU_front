package com.axedu.entity;

import java.time.OffsetDateTime;

import com.axedu.entity.enums.AccessType;

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

/** 원문·사건 파일 열람 기록 (complaint_access_logs) */
@Entity
@Table(name = "complaint_access_logs", indexes = {
        @Index(name = "idx_complaint_access_logs_complaint_id_created_at", columnList = "complaint_id, created_at"),
        @Index(name = "idx_complaint_access_logs_user_id_created_at", columnList = "user_id, created_at")})
@Getter
@Setter
@NoArgsConstructor
public class ComplaintAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_id", length = 32, nullable = false)
    private String complaintId;

    @Column(name = "user_id", length = 32, nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_type", nullable = false, length = 20)
    private AccessType accessType;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
