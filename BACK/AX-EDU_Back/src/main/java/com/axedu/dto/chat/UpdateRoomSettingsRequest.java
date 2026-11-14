package com.axedu.dto.chat;

import com.fasterxml.jackson.annotation.JsonSetter;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * PATCH /chat/rooms/{id}/settings. 보낸 키만 바꾼다.
 * availableHoursOverride 는 "키 없음"(유지)과 "null"(교사 기본 설정으로 되돌림)을 구분해야 해서 setter 로 받는다.
 */
@Schema(description = "채팅방 설정 변경 (보낸 키만 반영)")
public class UpdateRoomSettingsRequest {

    @Schema(description = "알림 끄기", example = "true")
    private Boolean muted;
    @Schema(description = "이 방만의 대화 가능 시간. null 이면 교사 기본 설정으로 되돌림", nullable = true)
    private TimeRange availableHoursOverride;
    private boolean availableHoursOverridePresent;

    public Boolean getMuted() {
        return muted;
    }

    public void setMuted(Boolean muted) {
        this.muted = muted;
    }

    public TimeRange getAvailableHoursOverride() {
        return availableHoursOverride;
    }

    @JsonSetter("availableHoursOverride")
    public void setAvailableHoursOverride(TimeRange availableHoursOverride) {
        this.availableHoursOverride = availableHoursOverride;
        this.availableHoursOverridePresent = true;
    }

    @Schema(hidden = true)
    public boolean isAvailableHoursOverridePresent() {
        return availableHoursOverridePresent;
    }
}
