package com.axedu.dto.complaint;

import java.time.OffsetDateTime;
import java.util.List;

import com.axedu.dto.user.UserSummary;
import com.axedu.entity.enums.EscalationStage;
import com.axedu.entity.enums.EscalationStatus;

/** Escalation (계약서 2.12) */
public record EscalationResponse(
        String id,
        List<String> reasonTypes,
        String description,
        EscalationStage stage,
        EscalationStatus status,
        UserSummary assignee,
        OffsetDateTime requestedAt) {
}
