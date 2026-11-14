package com.axedu.service;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;

import com.axedu.common.util.IdGenerator;

/**
 * 슈퍼스쿨 안심전화·면담 예약. DB 에는 예약 ID 만 보관한다 (DB 설계서 6장 최소 수집).
 * TODO: 슈퍼스쿨 안심전화·면담 예약 API 연동. 지금은 예약 ID 만 만들어 돌려준다.
 */
@Component
public class ReservationClient {

    public String reserveSafeCall(String teacherId, String parentId, OffsetDateTime scheduledAt) {
        return IdGenerator.newId(IdGenerator.SAFE_CALL);
    }

    public String reserveVisit(String teacherId, String parentId, OffsetDateTime scheduledAt) {
        return IdGenerator.newId(IdGenerator.VISIT);
    }
}
