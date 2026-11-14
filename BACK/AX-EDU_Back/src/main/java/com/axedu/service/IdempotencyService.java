package com.axedu.service;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.axedu.common.exception.ApiException;
import com.axedu.common.util.TimeUtil;
import com.axedu.entity.IdempotencyKey;
import com.axedu.repository.IdempotencyKeyRepository;

import lombok.RequiredArgsConstructor;

/**
 * Idempotency-Key 헤더 처리 (민원 생성·제출). 처음 만든 리소스 ID 를 24시간 기억한다.
 * TODO: 매일 expires_at 지난 행 정리 배치
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;

    @Transactional(readOnly = true)
    public Optional<String> find(String userId, String key, String endpoint) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        check(key);
        return repository.findById(new IdempotencyKey.Pk(userId, key))
                .filter(k -> k.getEndpoint().equals(endpoint) && k.getExpiresAt().isAfter(TimeUtil.now()))
                .map(k -> (String) k.getResponseBody().get("resourceId"));
    }

    @Transactional
    public void remember(String userId, String key, String endpoint, int status, String resourceId) {
        if (key == null || key.isBlank()) {
            return;
        }
        check(key);
        IdempotencyKey k = repository.findById(new IdempotencyKey.Pk(userId, key)).orElseGet(IdempotencyKey::new);
        k.setUserId(userId);
        k.setIdemKey(key);
        k.setEndpoint(endpoint);
        k.setResponseStatus((short) status);
        k.setResponseBody(Map.of("resourceId", resourceId));
        k.setExpiresAt(TimeUtil.now().plusHours(24));
        repository.save(k);
    }

    private static void check(String key) {
        if (key.length() > 64) {
            throw ApiException.validation("요청 형식이 올바르지 않습니다.", "Idempotency-Key", "max 64");
        }
    }
}
