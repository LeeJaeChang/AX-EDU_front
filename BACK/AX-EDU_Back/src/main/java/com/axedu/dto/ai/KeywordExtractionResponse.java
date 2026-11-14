package com.axedu.dto.ai;

import java.util.List;

import com.axedu.dto.law.LawSearchResult;
import com.axedu.entity.enums.ComplaintTag;
import com.axedu.service.ai.keyword.KeywordModels;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "민원 키워드 추출 결과")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KeywordExtractionResponse(
        @Schema(description = "사용한 모델 (키가 없으면 rule-based)", example = "gpt-4.1-mini") String model,
        @Schema(description = "사실관계 한 문장 요약", example = "수업 중 휴대전화를 사용한 학생의 휴대전화를 교사가 일주일째 돌려주지 않음") String situationSummary,
        @Schema(description = "법령으로 따져 볼 쟁점") List<String> legalIssues,
        @Schema(description = "검색 키워드와 근거 (중요한 순서)") List<KeywordModels.Keyword> keywords,
        @Schema(description = "법령 조회에 넣는 키워드", example = "[\"휴대전화\", \"압수\", \"학생생활지도\"]") List<String> searchKeywords,
        @Schema(description = "민원 분류") ComplaintTag category,
        @Schema(description = "searchLaws=true 일 때 법령 조회 결과 (조회 서버가 꺼져 있으면 생략, meta.lawSearch 참고)") List<LawSearchResult> laws) {

    public static KeywordExtractionResponse of(KeywordModels.Result r, List<LawSearchResult> laws) {
        return new KeywordExtractionResponse(r.model(), r.situationSummary(), r.legalIssues(), r.keywords(), r.terms(),
                r.category(), laws);
    }
}
