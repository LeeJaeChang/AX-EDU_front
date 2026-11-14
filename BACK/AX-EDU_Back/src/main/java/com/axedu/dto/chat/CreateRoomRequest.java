package com.axedu.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "1:1 채팅방 만들기")
public record CreateRoomRequest(
        @Schema(description = "대화 상대 사용자 ID", example = "usr_s6", requiredMode = Schema.RequiredMode.REQUIRED) String counterpartId) {
}
