package com.axedu.dto.chat;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** 프론트는 첨부가 있어도 type 을 항상 TEXT 로 보낸다 → 서버가 IMAGE/FILE 결정 */
@Schema(description = "메시지 보내기")
public record SendMessageRequest(
        @Schema(description = "클라이언트가 만든 메시지 ID (재전송 시 같은 값 → 기존 메시지 반환)", example = "c_20261114_0001") String clientMessageId,
        @Schema(description = "항상 TEXT 로 보내면 서버가 첨부를 보고 IMAGE/FILE 로 정함", example = "TEXT") String type,
        @Schema(description = "본문", example = "안녕하세요 선생님!") String content,
        @Schema(description = "POST /files 로 받은 파일 ID 목록") List<String> attachmentIds) {
}
