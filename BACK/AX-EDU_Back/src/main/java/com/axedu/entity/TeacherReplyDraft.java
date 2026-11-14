package com.axedu.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** STEP 3 AI 입장 정리 초안 (teacher_reply_drafts). 발송되지 않음 */
@Entity
@Table(name = "teacher_reply_drafts", indexes = {
        @Index(name = "idx_teacher_reply_drafts_complaint_id_created_at", columnList = "complaint_id, created_at"),
        @Index(name = "idx_teacher_reply_drafts_teacher_id", columnList = "teacher_id")})
@Getter
@Setter
@NoArgsConstructor
public class TeacherReplyDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_id", length = 32, nullable = false)
    private String complaintId;

    @Column(name = "teacher_id", length = 32, nullable = false)
    private String teacherId;

    @Column(name = "input_situation", columnDefinition = "TEXT")
    private String inputSituation;

    @Column(name = "input_reason", columnDefinition = "TEXT")
    private String inputReason;

    @Column(name = "input_next_actions", columnDefinition = "TEXT")
    private String inputNextActions;

    @Column(name = "draft_text", columnDefinition = "TEXT", nullable = false)
    private String draftText;

    @Column(name = "model_name", length = 50, nullable = false)
    private String modelName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
