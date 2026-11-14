package com.axedu.config;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import com.axedu.common.security.JwtTokenProvider;

/**
 * 시연용 데모 계정 (LocalSeedData 의 사용자와 같다). 프론트 '보는 사람' 선택에서 이 계정들의 고정 토큰을 쓴다.
 * 토큰은 로그인 API 없이 서버의 서명 키(AXEDU_JWT_SECRET)로 미리 만들어 Vercel 환경 변수에 넣는다.
 */
public final class DemoAccounts {

    private DemoAccounts() {
    }

    /** 고정 토큰의 발급 시각 (고정값이라야 같은 키로 언제 만들어도 같은 토큰) */
    static final Instant ISSUED_AT = Instant.parse("2026-11-01T00:00:00Z");

    /**
     * @param envName 프론트(Vercel)에 넣을 환경 변수 이름
     */
    public record Account(String userId, String label, String role, String name, List<String> childStudentIds, String envName) {
    }

    public static final List<Account> ACCOUNTS = List.of(
            new Account("usr_t1", "김수학 교사 (3학년 9반 담임)", "TEACHER", "김수학", List.of(), "VITE_DEMO_TOKEN_USR_T1"),
            new Account("usr_p1", "김리현 학부모 (자녀 김리현 30901)", "PARENT", "김리현 학부모", List.of("usr_s1"), "VITE_DEMO_TOKEN_USR_P1"),
            new Account("usr_p2", "김민수 학부모 (자녀 김민수 30902)", "PARENT", "김민수 학부모", List.of("usr_s5"), "VITE_DEMO_TOKEN_USR_P2"));

    public record Token(Account account, String token) {
    }

    public static List<Token> tokens(JwtTokenProvider provider, OffsetDateTime expiresAt) {
        return ACCOUNTS.stream()
                .map(a -> new Token(a, provider.issueFixed(a.userId(), a.role(), LocalSeedData.SCHOOL, a.name(), a.childStudentIds(),
                        ISSUED_AT, expiresAt.toInstant())))
                .toList();
    }

    /** Vercel 에 붙여 넣기 좋은 모양 (KEY=VALUE, 한 줄에 하나) */
    public static String asEnvLines(List<Token> tokens) {
        StringBuilder sb = new StringBuilder();
        for (Token t : tokens) {
            sb.append("# ").append(t.account().label()).append('\n')
                    .append(t.account().envName()).append('=').append(t.token()).append('\n');
        }
        return sb.toString();
    }
}
