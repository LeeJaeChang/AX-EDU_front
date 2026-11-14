package com.axedu.dto.chat;

import java.time.OffsetDateTime;

import com.axedu.dto.user.UserSummary;
import com.axedu.entity.enums.RoomType;
import com.fasterxml.jackson.annotation.JsonProperty;

/** ChatRoomDetail (계약서 2.3). availableHours 는 실제 적용 시간 */
public record ChatRoomDetailResponse(
        String id,
        RoomType type,
        String title,
        UserSummary counterpart,
        TimeRange availableHours,
        @JsonProperty("isWithinAvailableHours") boolean isWithinAvailableHours,
        LastMessage lastMessage,
        int unreadCount,
        boolean muted,
        String activeComplaintId,
        OffsetDateTime createdAt,
        String inputPlaceholder,
        boolean canSend,
        boolean canFileComplaint,
        boolean canRequestHelp) {

    public static ChatRoomDetailResponse of(ChatRoomResponse r, TimeRange effectiveHours, String inputPlaceholder,
                                            boolean canSend, boolean canFileComplaint, boolean canRequestHelp) {
        return new ChatRoomDetailResponse(r.id(), r.type(), r.title(), r.counterpart(), effectiveHours,
                r.isWithinAvailableHours(), r.lastMessage(), r.unreadCount(), r.muted(), r.activeComplaintId(), r.createdAt(),
                inputPlaceholder, canSend, canFileComplaint, canRequestHelp);
    }
}
