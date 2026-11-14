package com.axedu.service.law;

/**
 * 법령 조회 서버 호출 실패 (서버 꺼짐·시간 초과·잘못된 응답).
 * 민원 흐름에서는 법령 근거 없이도 진행할지 호출하는 쪽이 정한다.
 */
public class LawSearchException extends RuntimeException {

    public LawSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
