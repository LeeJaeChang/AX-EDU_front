package com.axedu.common.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * 액세스 토큰 검증·발급. payload: userId, role, schoolId, name (+ 학부모는 childStudentIds) — 계약서 0장 13번
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final JwtProperties props;

    public JwtTokenProvider(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    /** 서명·만료를 검증하고 userId 를 돌려준다. 잘못된 토큰이면 null */
    public String parseUserId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Object userId = claims.get("userId");
            if (userId == null) {
                userId = claims.getSubject();
            }
            return userId == null ? null : userId.toString();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 고정 토큰 (시연용 데모 계정). 발급·만료 시각과 클레임 순서를 고정해 같은 서명 키면 언제 만들어도 같은 문자열이 나온다.
     * 그래서 미리 만들어 프론트 환경 변수에 넣어 두고, 서버를 다시 배포해도 그대로 쓸 수 있다.
     */
    public String issueFixed(String userId, String role, String schoolId, String name, List<String> childStudentIds,
                             Instant issuedAt, Instant expiresAt) {
        var builder = Jwts.builder()
                .subject(userId)
                .claim("userId", userId)
                .claim("role", role)
                .claim("schoolId", schoolId)
                .claim("name", name);
        if (childStudentIds != null && !childStudentIds.isEmpty()) {
            builder.claim("childStudentIds", childStudentIds);
        }
        return builder.issuedAt(Date.from(issuedAt)).expiration(Date.from(expiresAt)).signWith(key).compact();
    }

    /** 개발용 토큰 발급 (local 프로필의 /dev/token 에서만 사용). 운영 토큰은 슈퍼스쿨이 발급 */
    public String issue(String userId, String role, String schoolId, String name, List<String> childStudentIds) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(userId)
                .claims(Map.of("userId", userId, "role", role, "schoolId", schoolId, "name", name))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.devTokenTtlMinutes(), ChronoUnit.MINUTES)));
        if (childStudentIds != null && !childStudentIds.isEmpty()) {
            builder.claim("childStudentIds", childStudentIds);
        }
        return builder.signWith(key).compact();
    }
}
