package com.axedu.common.exception;

import org.springframework.http.HttpStatus;

/** 계약서 1.3 에러 코드 */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "이 기능을 쓸 권한이 없습니다."),
    CHAT_OUTSIDE_AVAILABLE_HOURS(HttpStatus.FORBIDDEN, "지금은 대화 가능 시간이 아닙니다."),
    NOTICE_ROOM_READ_ONLY(HttpStatus.FORBIDDEN, "공지형 채팅방에는 메시지를 보낼 수 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 정보를 찾을 수 없습니다."),
    ROOM_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 대화 중인 상대입니다."),
    INVALID_COMPLAINT_STATUS(HttpStatus.CONFLICT, "지금 상태에서는 할 수 없는 요청입니다."),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "파일이 너무 큽니다."),
    AI_PROCESSING_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "AI 응답이 늦어지고 있어요. 잠시 후 다시 시도해 주세요."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
