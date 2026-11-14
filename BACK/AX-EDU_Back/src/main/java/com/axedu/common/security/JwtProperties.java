package com.axedu.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * axedu.jwt.secret: HS256 서명 키(32바이트 이상). 실제 운영에서는 슈퍼스쿨이 발급한 토큰의 검증 키를 넣는다.
 */
@ConfigurationProperties(prefix = "axedu.jwt")
public record JwtProperties(String secret, long devTokenTtlMinutes) {
}
