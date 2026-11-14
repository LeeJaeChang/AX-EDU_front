package com.axedu.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;

/** {"from":"HH:mm","to":"HH:mm"} */
@Schema(description = "시간 범위 (HH:mm, KST)")
public record TimeRange(
        @Schema(description = "시작", example = "09:00") String from,
        @Schema(description = "끝", example = "18:00") String to) {
}
