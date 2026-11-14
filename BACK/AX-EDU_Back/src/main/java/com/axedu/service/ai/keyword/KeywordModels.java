package com.axedu.service.ai.keyword;

import java.util.List;

import com.axedu.entity.enums.ComplaintTag;

import io.swagger.v3.oas.annotations.media.Schema;

/** 민원 키워드 추출(AI 1단계) 입출력 */
public final class KeywordModels {

    private KeywordModels() {
    }

    /**
     * @param complaintText 학부모 민원 원문 (가명처리된 것. 실제 흐름에서는 ComplaintService.maskedText)
     * @param history       민원 관련 채팅 내역 (오래된 것부터, 없으면 빈 목록)
     */
    public record Input(String complaintText, List<ChatLine> history) {

        public static Input of(String complaintText) {
            return new Input(complaintText, List.of());
        }
    }

    @Schema(description = "채팅 한 줄")
    public record ChatLine(
            @Schema(description = "말한 사람 (예: 학부모, 교사, AI)", example = "학부모") String speaker,
            @Schema(description = "내용", example = "폰을 일주일째 안 돌려주세요") String text) {
    }

    @Schema(description = "검색 키워드와 근거")
    public record Keyword(
            @Schema(description = "검색 키워드 (법령 용어)", example = "휴대전화") String term,
            @Schema(description = "근거가 된 민원 표현", example = "폰을 수업시간에 잠깐 봤다고") String evidence) {
    }

    /**
     * @param situationSummary 사실관계 한 문장 요약
     * @param legalIssues      법령으로 따져 볼 쟁점 (질문 형태)
     * @param keywords         검색 키워드 (중요한 순서, 정리됨)
     * @param category         민원 분류
     * @param model            사용한 모델 (규칙 기반이면 "rule-based")
     */
    public record Result(String situationSummary, List<String> legalIssues, List<Keyword> keywords,
                         ComplaintTag category, String model) {

        /** 법령 조회에 넣을 키워드 문자열 */
        public List<String> terms() {
            return keywords.stream().map(Keyword::term).toList();
        }
    }

    /** AI 가 돌려주는 JSON (prompts/keyword-extraction/schema.json) */
    record LlmOutput(String situationSummary, List<String> legalIssues, List<Keyword> keywords, String category) {
    }
}
