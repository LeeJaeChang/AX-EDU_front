package com.axedu.common.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 서비스 계층에서 던지는 예외. GlobalExceptionHandler 가 실패 봉투로 바꾼다. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> details;

    public ApiException(ErrorCode code) {
        this(code, code.defaultMessage(), null);
    }

    public ApiException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public ApiException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public ErrorCode getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    // ---- 자주 쓰는 모양 ----

    public static ApiException notFound(String message) {
        return new ApiException(ErrorCode.NOT_FOUND, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(ErrorCode.FORBIDDEN, message);
    }

    /** 400 VALIDATION_ERROR. details.fields = {필드: 사유}. message 는 첫 번째 사유 */
    public static ApiException validation(Map<String, String> fields) {
        String first = fields.values().iterator().next();
        return validation(first, fields);
    }

    public static ApiException validation(String message, Map<String, String> fields) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, message, Map.of("fields", new LinkedHashMap<>(fields)));
    }

    public static ApiException validation(String message, String field, String reason) {
        return validation(message, Map.of(field, reason));
    }
}
