package com.axedu.dto.ai;

import java.util.List;

import com.axedu.service.ai.keyword.KeywordModels;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "민원 키워드 추출 (AI 1단계)")
public record KeywordExtractionRequest(
        @Schema(description = "학부모 민원 내용 (최대 2000자). 이름 등은 가려서 보내는 것을 권장",
                example = "선생님, 저희 아이 폰을 수업시간에 잠깐 봤다고 일주일째 안 돌려주신다네요. 이렇게 오래 뺏어가도 되나요?",
                requiredMode = Schema.RequiredMode.REQUIRED) String text,
        @Schema(description = "민원 관련 채팅 내역 (오래된 것부터, 선택)") List<KeywordModels.ChatLine> history,
        @Schema(description = "true 면 뽑은 키워드로 법령 조회까지 해서 laws 에 담음 (파이썬 법령 조회 서버 필요)", example = "true") Boolean searchLaws,
        @Schema(description = "법령 조회 결과 수 (1~20, 기본 6)", example = "6") Integer lawLimit) {
}
