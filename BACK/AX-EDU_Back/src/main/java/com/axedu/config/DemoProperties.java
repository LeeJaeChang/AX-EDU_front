package com.axedu.config;

import java.time.OffsetDateTime;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 시연용 데모 데이터·계정 (axedu.demo.*).
 *
 * @param enabled        true 면 DB 가 비어 있을 때 데모 사용자·방·민원을 넣고, 데모 계정 고정 토큰을 시작 로그에 남긴다.
 *                       local 프로필은 항상 true, 배포(prod)는 AXEDU_DEMO_ENABLED=true 로 켠다
 * @param tokenExpiresAt 데모 계정 고정 토큰 만료 시각 (시연이 끝나면 지나도록)
 */
@ConfigurationProperties(prefix = "axedu.demo")
public record DemoProperties(boolean enabled, OffsetDateTime tokenExpiresAt) {
}
