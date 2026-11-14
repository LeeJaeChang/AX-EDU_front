package com.axedu.common.response;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 공통 응답 봉투 (계약서 1.2).
 * 성공: {"success":true,"data":...,"meta":{...}} / 실패: {"success":false,"error":{code,message,details}}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, Map<String, Object> meta, ErrorBody error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> ok(T data, Map<String, Object> meta) {
        return new ApiResponse<>(true, data, meta, null);
    }

    public static ApiResponse<Void> fail(String code, String message, Map<String, Object> details) {
        return new ApiResponse<>(false, null, null, new ErrorBody(code, message, details));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorBody(String code, String message, Map<String, Object> details) {
    }
}
