package com.axedu.dto.complaint;

import com.axedu.dto.user.UserSummary;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/** GET /complaints/{id}/recipients — 민원을 받을 수 있는 선생님 (카드 단계 '받는 선생님' 목록) */
@Schema(description = "민원을 받을 수 있는 선생님")
public record ComplaintRecipientResponse(
        UserSummary teacher,
        @Schema(description = "담당 표시", example = "3학년 9반 담임 · 수학") String label,
        @Schema(description = "기본으로 고를 선생님 (한 명)") boolean recommended,
        @Schema(description = "추천 이유 (추천 선생님만)", example = "담임 선생님이 먼저 상황을 확인해요.")
        @JsonInclude(JsonInclude.Include.NON_NULL) String reason) {
}
