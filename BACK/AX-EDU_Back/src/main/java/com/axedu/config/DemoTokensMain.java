package com.axedu.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import com.axedu.common.security.JwtProperties;
import com.axedu.common.security.JwtTokenProvider;

/**
 * 서버를 띄우지 않고 데모 계정 고정 토큰을 만든다: ./gradlew demoTokens
 * 서명 키는 환경 변수 AXEDU_JWT_SECRET 또는 프로젝트 루트 .env 에서 읽는다 (배포 서버와 같은 값이어야 함).
 * 만료 시각은 AXEDU_DEMO_TOKEN_EXPIRES_AT (기본 application.yml 과 같은 값).
 */
public final class DemoTokensMain {

    static final String DEFAULT_EXPIRES_AT = "2026-12-31T23:59:59+09:00";

    private DemoTokensMain() {
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> env = new HashMap<>(readDotEnv(Path.of(".env")));
        env.putAll(System.getenv());
        String secret = env.get("AXEDU_JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            System.err.println("AXEDU_JWT_SECRET 이 없습니다. 배포 서버(Railway)와 같은 값을 환경 변수나 .env 에 넣고 다시 실행하세요.");
            System.exit(1);
        }
        if (secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            System.err.println("AXEDU_JWT_SECRET 은 32바이트 이상이어야 합니다.");
            System.exit(1);
        }
        String raw = env.get("AXEDU_DEMO_TOKEN_EXPIRES_AT");
        OffsetDateTime expiresAt = OffsetDateTime.parse(raw == null || raw.isBlank() ? DEFAULT_EXPIRES_AT : raw.trim());
        JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties(secret.trim(), 0));
        System.out.println("# 데모 계정 고정 토큰 (만료 " + expiresAt + ") — 프론트 Vercel 환경 변수에 넣으세요");
        System.out.print(DemoAccounts.asEnvLines(DemoAccounts.tokens(provider, expiresAt)));
    }

    /** KEY=VALUE 줄만 읽는다 (# 주석·빈 줄 무시) */
    static Map<String, String> readDotEnv(Path file) throws IOException {
        Map<String, String> out = new HashMap<>();
        if (!Files.exists(file)) {
            return out;
        }
        for (String line : Files.readAllLines(file)) {
            String t = line.trim();
            int eq = t.indexOf('=');
            if (t.isEmpty() || t.startsWith("#") || eq <= 0) {
                continue;
            }
            out.put(t.substring(0, eq).trim(), t.substring(eq + 1).trim());
        }
        return out;
    }
}
