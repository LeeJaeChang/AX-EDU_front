package com.axedu.dto.chat;

import java.time.OffsetDateTime;
import java.util.List;

import com.axedu.dto.user.UserSummary;
import com.axedu.entity.enums.MessageType;

/** Message (계약서 2.4). REST 응답과 message.created 이벤트가 같은 모양 */
public record MessageResponse(
        String id,
        String roomId,
        MessageType type,
        UserSummary sender,
        String content,
        List<AttachmentResponse> attachments,
        String complaintId,
        String clientMessageId,
        boolean readByCounterpart,
        OffsetDateTime createdAt) {
}
