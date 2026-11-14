package com.axedu.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.axedu.common.security.JwtProperties;
import com.axedu.common.security.JwtTokenProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 데모가 켜져 있으면 시작할 때 데모 계정 고정 토큰을 로그에 남긴다 (Railway 로그에서 복사해 Vercel 에 넣을 수 있게).
 * 같은 서명 키면 매번 같은 토큰이다. 미리 만들려면 ./gradlew demoTokens
 */
@Slf4j
@Component
@Order(100) // 데모 데이터(LocalSeedData) 다음
@ConditionalOnProperty(prefix = "axedu.demo", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoTokenLogger implements ApplicationRunner {

    static final String DEV_SECRET = "axedu-local-dev-jwt-secret-change-me-0123456789";

    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;
    private final DemoProperties demo;

    @Override
    public void run(ApplicationArguments args) {
        if (DEV_SECRET.equals(jwtProperties.secret())) {
            log.warn("AXEDU_JWT_SECRET 가 개발용 기본값입니다. 배포에서는 꼭 바꾸세요 (토큰을 누구나 만들 수 있음).");
        }
        log.info("데모 계정 고정 토큰 (만료 {}) — 프론트 Vercel 환경 변수:\n{}", demo.tokenExpiresAt(),
                DemoAccounts.asEnvLines(DemoAccounts.tokens(tokenProvider, demo.tokenExpiresAt())));
    }
}
