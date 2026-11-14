package com.axedu.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "읽음 처리")
public record ReadRequest(
        @Schema(description = "여기까지 읽음 (생략하면 최신 메시지까지)", example = "msg_rh_4") String lastReadMessageId) {
}
