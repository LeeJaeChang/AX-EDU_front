package com.axedu.entity;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Idempotency-Key 헤더 응답 재사용 (idempotency_keys, 24시간).
 * response_body 에는 만들어진 리소스 ID 를 넣고, 재요청 시 그 리소스의 현재 모양을 다시 내려 준다.
 */
@Entity
@Table(name = "idempotency_keys", indexes = @Index(name = "idx_idempotency_keys_expires_at", columnList = "expires_at"))
@IdClass(IdempotencyKey.Pk.class)
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyKey {

    @Id
    @Column(name = "user_id", length = 32)
    private String userId;

    @Id
    @Column(name = "idem_key", length = 64)
    private String idemKey;

    @Column(length = 100, nullable = false)
    private String endpoint;

    @Column(name = "response_status", nullable = false)
    private short responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", nullable = false)
    private Map<String, Object> responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private String userId;
        private String idemKey;
    }
}
