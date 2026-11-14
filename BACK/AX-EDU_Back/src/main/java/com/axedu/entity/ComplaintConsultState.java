package com.axedu.entity;

import java.time.OffsetDateTime;

import com.axedu.entity.enums.ComplaintTag;
import com.axedu.entity.enums.ConsultStage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 민원당 AI 상담 진행 상태 (complaint_consult_states). 학부모 발화가 담긴 슬롯은 state_enc 에 암호화 */
@Entity
@Table(name = "complaint_consult_states")
@Getter
@Setter
@NoArgsConstructor
public class ComplaintConsultState {

    @Id
    @Column(name = "complaint_id", length = 32)
    private String complaintId;

    /** null = 첫 응답 전(AI 실패) 또는 카드 단계 이후 */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private ConsultStage stage;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ComplaintTag category;

    @Column(name = "proposed_teacher_id", length = 32)
    private String proposedTeacherId;

    @Column(name = "emotion_acked", nullable = false)
    private boolean emotionAcked;

    @Column(name = "state_enc", nullable = false, length = 65536)
    private byte[] stateEnc;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }
}
