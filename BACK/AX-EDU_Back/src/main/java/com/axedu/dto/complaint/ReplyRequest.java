package com.axedu.dto.complaint;

import java.time.OffsetDateTime;

import com.axedu.entity.enums.ContactMethod;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "답변 보내기 (교사)")
public record ReplyRequest(
        @Schema(description = "답변 본문", example = "안녕하세요, 리현이 담임 김수학입니다. 채점 기준을 안내드립니다.") String content,
        @Schema(description = "연락 방법", example = "MESSAGE") ContactMethod contactMethod,
        @Schema(description = "통화·방문 예정 시각 (CALL/VISIT 일 때)", example = "2026-11-20T16:00:00+09:00") OffsetDateTime scheduledAt) {
}
