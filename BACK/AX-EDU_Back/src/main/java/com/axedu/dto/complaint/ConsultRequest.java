package com.axedu.dto.complaint;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "상담 이어 가기 (학부모 답 한 번)")
public record ConsultRequest(
        @Schema(description = "학부모가 쓴 말 또는 누른 빠른 답의 label (1~2000자)", example = "어제 3교시 영어 시간이었어요",
                requiredMode = Schema.RequiredMode.REQUIRED) String content,
        @Schema(description = "빠른 답을 눌렀으면 그 value (있으면 content 보다 우선). 예: TO_TEACHER, URGENT, usr_t1, 16:00-17:00",
                example = "URGENT") String value) {
}
