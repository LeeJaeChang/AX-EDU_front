package com.axedu.dto.complaint;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "요약 카드 AI 수정")
public record RevisionRequest(
        @Schema(description = "수정 기준 카드 버전 (최신이 아니면 409)", example = "1") Integer baseVersion,
        @Schema(description = "수정 지시", example = "요청 사항을 좀 더 정중하게 바꿔 주세요.") String instruction) {
}
