package com.axedu.dto.complaint;

import com.axedu.entity.enums.UrgencyLevel;

import io.swagger.v3.oas.annotations.media.Schema;

/** 요약 카드 대응 시급도 (학부모·교사 모두) */
@Schema(description = "대응 시급도")
public record CardUrgency(
        @Schema(description = "URGENT 오늘 안에 / SOON 며칠 안에 / NORMAL 급하지 않음") UrgencyLevel level,
        @Schema(description = "사유 (예: 학생 안전·건강 관련). URGENT 가 아니면 null") String reason) {
}
