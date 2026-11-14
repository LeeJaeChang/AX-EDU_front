package com.axedu.entity;

import java.time.OffsetDateTime;

import com.axedu.entity.enums.MessageType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 방에 공개되는 메시지 (messages). 민원 AI 대화는 넣지 않는다 */
@Entity
@Table(name = "messages",
        uniqueConstraints = @UniqueConstraint(name = "uq_messages_sender_id_client_message_id", columnNames = {"sender_id", "client_message_id"}),
        indexes = {
                @Index(name = "idx_messages_room_id_id", columnList = "room_id, id"),
                @Index(name = "idx_messages_room_id_created_at", columnList = "room_id, created_at"),
                @Index(name = "idx_messages_complaint_id", columnList = "complaint_id"),
                @Index(name = "idx_messages_summary_card_id", columnList = "summary_card_id")})
@Getter
@Setter
@NoArgsConstructor
public class Message {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "room_id", length = 32, nullable = false)
    private String roomId;

    @Column(name = "school_id", length = 32, nullable = false)
    private String schoolId;

    /** SYSTEM 메시지는 NULL */
    @Column(name = "sender_id", length = 32)
    private String senderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageType type;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "complaint_id", length = 32)
    private String complaintId;

    @Column(name = "summary_card_id")
    private Long summaryCardId;

    @Column(name = "client_message_id", length = 64)
    private String clientMessageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
