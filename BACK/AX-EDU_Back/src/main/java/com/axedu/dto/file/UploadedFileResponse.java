package com.axedu.dto.file;

import java.time.OffsetDateTime;

/** UploadedFile (계약서 2.7) */
public record UploadedFileResponse(String id, String name, String mimeType, long size, String url, OffsetDateTime expiresAt) {
}
