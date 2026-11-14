package com.axedu.service.ai;

/** AI 호출 실패 (시간 초과, 형식 오류 등). 서비스가 422 AI_PROCESSING_FAILED 로 바꾼다 */
public class AiException extends RuntimeException {

    private final String errorCode;

    public AiException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
