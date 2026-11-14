package com.axedu.entity;

import java.io.Serializable;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 방 참여자와 사용자별 읽음·알림 상태 (chat_room_members) */
@Entity
@Table(name = "chat_room_members", indexes = @Index(name = "idx_chat_room_members_user_id_left_at", columnList = "user_id, left_at"))
@IdClass(ChatRoomMember.Pk.class)
@Getter
@Setter
@NoArgsConstructor
public class ChatRoomMember {

    @Id
    @Column(name = "room_id", length = 32)
    private String roomId;

    @Id
    @Column(name = "user_id", length = 32)
    private String userId;

    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt;

    /** '나가기' 시각. NULL 이면 참여 중 */
    @Column(name = "left_at")
    private OffsetDateTime leftAt;

    @Column(nullable = false)
    private boolean muted;

    @Column(name = "last_read_message_id", length = 32)
    private String lastReadMessageId;

    @Column(name = "unread_count", nullable = false)
    private int unreadCount;

    public ChatRoomMember(String roomId, String userId) {
        this.roomId = roomId;
        this.userId = userId;
    }

    public boolean isActive() {
        return leftAt == null;
    }

    @PrePersist
    void onCreate() {
        if (joinedAt == null) {
            joinedAt = OffsetDateTime.now();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private String roomId;
        private String userId;
    }
}
