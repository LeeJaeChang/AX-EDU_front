package com.axedu.dto.complaint;

import java.time.OffsetDateTime;

import com.axedu.entity.enums.ContactMethod;

/** TeacherReply (계약서 2.11) */
public record TeacherReplyResponse(String content, ContactMethod contactMethod, OffsetDateTime scheduledAt, OffsetDateTime sentAt) {
}
