package com.axedu.dto.law;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** 법령 조회 결과 한 건 (조문 또는 판례·결정문의 한 단락) */
@Schema(description = "법령 조회 결과 (조문 또는 판례·결정문 단락)")
public record LawSearchResult(
        @Schema(description = "청크 ID (AI_DATA/chunks.jsonl)", example = "law-283357-18") String chunkId,
        @Schema(description = "자료 종류", example = "법령") String type,
        @Schema(description = "문서 제목", example = "교원의 지위 향상 및 교육활동 보호를 위한 특별법") String title,
        @Schema(description = "조문 번호 (법령·고시·조례)", example = "제19조") String article,
        @Schema(description = "조문 제목 또는 판례·결정문 섹션", example = "교육활동 침해행위") String articleTitle,
        @Schema(description = "사건번호 (판례·결정례·해석례)") String caseNo,
        @Schema(description = "제정 기관 (자치법규)") String org,
        @Schema(description = "법령 종류 (법률·대통령령·고시 등)") String lawKind,
        @Schema(description = "시행일·선고일", example = "20260219") String effectiveDate,
        @Schema(description = "원문 링크 (없으면 null)") String sourceUrl,
        @Schema(description = "조문·단락 전문 (AI 요약 입력용)") String text,
        @Schema(description = "키워드 주변 미리보기 (약 180자)") String snippet,
        @Schema(description = "이 결과에서 일치한 키워드") List<String> matchedKeywords,
        @Schema(description = "관련도 점수 (클수록 관련 높음, 질의마다 상대값)", example = "33.8") double score) {
}
