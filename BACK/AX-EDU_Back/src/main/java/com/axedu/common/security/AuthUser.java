package com.axedu.common.security;

import com.axedu.entity.enums.UserRole;

/** 요청을 보낸 로그인 사용자. 토큰의 userId 로 users 테이블을 읽어 만든다. */
public record AuthUser(String id, UserRole role, String schoolId, String name) {

    public boolean is(UserRole r) {
        return role == r;
    }
}
