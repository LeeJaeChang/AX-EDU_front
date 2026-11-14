package com.axedu.dto.complaint;

import java.time.OffsetDateTime;

import com.axedu.entity.enums.ComplaintStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

/** POST /complaints/{id}/reply 응답. CALL → safeCall, VISIT → visit, MESSAGE → 둘 다 없음 */
public record ReplyResultResponse(
        ComplaintStatus status,
        String messageId,
        @JsonInclude(JsonInclude.Include.NON_NULL) Reservation safeCall,
        @JsonInclude(JsonInclude.Include.NON_NULL) Reservation visit) {

    public record Reservation(String reservationId, OffsetDateTime scheduledAt) {
    }
}
