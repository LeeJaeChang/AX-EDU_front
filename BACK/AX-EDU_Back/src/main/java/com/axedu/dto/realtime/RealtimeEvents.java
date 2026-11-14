package com.axedu.dto.realtime;

import java.time.OffsetDateTime;

import com.axedu.dto.chat.TimeRange;
import com.axedu.entity.enums.ComplaintStatus;

/** WebSocket 이벤트 data 모양 (계약서 5장) */
public final class RealtimeEvents {

    public static final String MESSAGE_CREATED = "message.created";
    public static final String MESSAGE_READ = "message.read";
    public static final String ROOM_CREATED = "room.created";
    public static final String ROOM_UPDATED = "room.updated";
    public static final String ROOM_UNREAD_CHANGED = "room.unread_changed";
    public static final String COMPLAINT_STATUS_CHANGED = "complaint.status_changed";
    public static final String COMPLAINT_CARD_UPDATED = "complaint.card_updated";
    public static final String ESCALATION_CREATED = "escalation.created";
    public static final String ESCALATION_UPDATED = "escalation.updated";

    private RealtimeEvents() {
    }

    public record MessageRead(String roomId, String readerId, String lastReadMessageId) {
    }

    public record RoomUpdated(String roomId, TimeRange availableHours, String inputPlaceholder, boolean canSend) {
    }

    public record UnreadChanged(String roomId, int unreadCount) {
    }

    public record StatusChanged(String complaintId, ComplaintStatus from, ComplaintStatus to, OffsetDateTime changedAt) {
    }

    public record CardUpdated(String complaintId, int version) {
    }
}
