package com.axedu.dto.chat;

/** Attachment (계약서 2.4). 이미지면 thumbnailUrl 에 썸네일(없으면 url) */
public record AttachmentResponse(String id, String name, String mimeType, long size, String url, String thumbnailUrl) {
}
