package com.axedu.dto.chat;

import java.time.OffsetDateTime;

import com.axedu.dto.user.UserSummary;
import com.axedu.entity.enums.RoomType;
import com.fasterxml.jackson.annotation.JsonProperty;

/** ChatRoom (계약서 2.2). availableHours 는 방별 재정의 값(없으면 null) */
public record ChatRoomResponse(
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
        OffsetDateTime createdAt) {
}
