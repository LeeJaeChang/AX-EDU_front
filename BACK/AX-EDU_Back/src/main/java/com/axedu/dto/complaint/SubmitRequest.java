package com.axedu.dto.complaint;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "확인하고 보내기")
public record SubmitRequest(
        @Schema(description = "학부모가 확인한 카드 버전 (최신이 아니면 409)", example = "1") Integer cardVersion,
        @Schema(description = "받는 선생님 ID (GET /complaints/{id}/recipients 중 하나). "
                + "방에서 시작한 민원은 생략 가능하고, 주면 그 방의 선생님과 같아야 함", example = "usr_t1") String teacherId) {
}
