package com.axedu.entity;

import java.time.LocalTime;
import java.time.OffsetDateTime;

import com.axedu.entity.enums.RoomType;

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

/** 채팅방 (chat_rooms) */
@Entity
@Table(name = "chat_rooms", indexes = {
        @Index(name = "idx_chat_rooms_school_id_last_message_at", columnList = "school_id, last_message_at"),
        @Index(name = "idx_chat_rooms_owner_id", columnList = "owner_id")})
@Getter
@Setter
@NoArgsConstructor
public class ChatRoom {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "school_id", length = 32, nullable = false)
    private String schoolId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomType type;

    /** GROUP·NOTICE 방 제목. DIRECT 는 NULL (조회 시 상대 정보로 조합) */
    @Column(length = 100)
    private String title;

    /** DIRECT 중복 방지 키: 정렬된 두 사용자 ID 를 ':' 로 연결 */
    @Column(name = "direct_key", length = 70, unique = true)
    private String directKey;

    @Column(name = "owner_id", length = 32)
    private String ownerId;

    @Column(name = "available_from_override")
    private LocalTime availableFromOverride;

    @Column(name = "available_to_override")
    private LocalTime availableToOverride;

    @Column(name = "last_message_id", length = 32)
    private String lastMessageId;

    @Column(name = "last_message_at")
    private OffsetDateTime lastMessageAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static String directKeyOf(String a, String b) {
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }

    public boolean hasOverride() {
        return availableFromOverride != null && availableToOverride != null;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
