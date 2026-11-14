package com.axedu.entity;

import java.time.OffsetDateTime;

import com.axedu.entity.enums.FilePurpose;
import com.axedu.entity.enums.FileStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 업로드 파일 메타데이터 (files). 실제 파일은 스토리지에 있고 URL 은 조회 시 서명 발급 */
@Entity
@Table(name = "files", indexes = {
        @Index(name = "idx_files_uploader_id", columnList = "uploader_id"),
        @Index(name = "idx_files_expires_at", columnList = "expires_at")})
@Getter
@Setter
@NoArgsConstructor
public class StoredFile {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "school_id", length = 32, nullable = false)
    private String schoolId;

    @Column(name = "uploader_id", length = 32, nullable = false)
    private String uploaderId;

    @Column(name = "original_name", length = 255, nullable = false)
    private String originalName;

    @Column(name = "mime_type", length = 100, nullable = false)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(name = "storage_key", length = 500, nullable = false)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FilePurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FileStatus status = FileStatus.TEMP;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public boolean isImage() {
        return mimeType != null && mimeType.startsWith("image/");
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
