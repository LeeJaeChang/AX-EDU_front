package com.axedu.dto.chat;

import java.time.OffsetDateTime;

import com.axedu.entity.enums.MessageType;

public record LastMessage(String id, MessageType type, String preview, OffsetDateTime sentAt) {
}
