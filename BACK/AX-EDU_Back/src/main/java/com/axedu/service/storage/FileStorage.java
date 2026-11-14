package com.axedu.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;

import org.springframework.core.io.Resource;

/**
 * 파일 실제 저장소. DB 에는 storage_key 만 두고 URL 은 조회할 때마다 만료 시간이 있는 서명 URL 로 발급한다.
 * 지금은 로컬 디스크 구현(LocalFileStorage)만 있다. TODO: 운영은 오브젝트 스토리지(S3 호환) 구현으로 교체
 */
public interface FileStorage {

    void save(String storageKey, InputStream in) throws IOException;

    Resource load(String storageKey);

    /** fileId 로 여는 서명 URL */
    String signedUrl(String fileId, OffsetDateTime expiresAt);

    /** 서명 URL 검증 */
    boolean verify(String fileId, long expiresEpochSeconds, String signature);
}
