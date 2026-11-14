package com.axedu.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** AI 상담 대화 한 줄 (complaint_consult_messages). 본문은 원문처럼 암호화, 교사에게는 내려가지 않는다 */
@Entity
@Table(name = "complaint_consult_messages",
        uniqueConstraints = @UniqueConstraint(name = "uq_complaint_consult_messages_complaint_id_seq", columnNames = {"complaint_id", "seq"}))
@Getter
@Setter
@NoArgsConstructor
public class ComplaintConsultMessage {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "complaint_id", length = 32, nullable = false)
    private String complaintId;

    @Column(nullable = false)
    private int seq;

    /** PARENT | AI */
    @Column(length = 8, nullable = false)
    private String sender;

    @Column(name = "body_enc", nullable = false, length = 65536)
    private byte[] bodyEnc;

    /** 학부모가 누른 빠른 답 값 (value 는 H2 예약어라 컬럼 이름은 reply_value) */
    @Column(name = "reply_value", length = 64)
    private String value;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
