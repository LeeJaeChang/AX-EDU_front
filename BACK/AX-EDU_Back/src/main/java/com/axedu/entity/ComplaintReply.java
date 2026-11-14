package com.axedu.entity;

import java.time.OffsetDateTime;

import com.axedu.entity.enums.ContactMethod;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** STEP 4 '학부모에게 보낼 연락' (complaint_replies). 민원당 1건 */
@Entity
@Table(name = "complaint_replies", indexes = {
        @Index(name = "idx_complaint_replies_teacher_id", columnList = "teacher_id"),
        @Index(name = "idx_complaint_replies_based_on_draft_id", columnList = "based_on_draft_id")})
@Getter
@Setter
@NoArgsConstructor
public class ComplaintReply {

    @Id
    @Column(name = "complaint_id", length = 32)
    private String complaintId;

    @Column(name = "teacher_id", length = 32, nullable = false)
    private String teacherId;

    @Column(name = "message_id", length = 32, nullable = false, unique = true)
    private String messageId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_method", nullable = false, length = 20)
    private ContactMethod contactMethod;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "safe_call_reservation_id", length = 64)
    private String safeCallReservationId;

    @Column(name = "visit_reservation_id", length = 64)
    private String visitReservationId;

    @Column(name = "based_on_draft_id")
    private Long basedOnDraftId;

    @Column(name = "sent_at", nullable = false)
    private OffsetDateTime sentAt;
}
