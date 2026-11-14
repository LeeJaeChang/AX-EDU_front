package com.axedu.dto.law;

import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 키워드 기반 법령 조회 요청. 파이썬 법령 조회 서버(ai/law_search_server.py)의 POST /search 본문과 같은 모양.
 */
@Schema(description = "키워드 기반 법령 조회")
public record LawSearchRequest(
        @ArraySchema(arraySchema = @Schema(description = "검색 키워드 (AI 가 민원에서 뽑은 것). 여러 단어 키워드 가능",
                example = "[\"휴대전화\", \"압수\", \"생활지도\"]", requiredMode = Schema.RequiredMode.REQUIRED),
                maxItems = 20)
        List<String> keywords,
        @Schema(description = "최대 결과 수 (1~50, 기본 10)", example = "10") Integer limit,
        @ArraySchema(schema = @Schema(allowableValues = {"법령", "행정규칙", "판례", "법령해석례", "인권위결정례", "자치법규"}),
                arraySchema = @Schema(description = "자료 종류 필터. 생략하면 전체"))
        List<String> types,
        @Schema(description = "자료 종류별 최대 개수. 법령·판례 등이 골고루 섞이게 할 때 (생략하면 제한 없음)", example = "4") Integer perType,
        @Schema(description = "같은 문서(법령·판례 하나)에서 최대 몇 개 조문·단락까지 (기본 2)", example = "2") Integer perDoc,
        @Schema(description = "자치법규를 이 기관 것만 남김 (예: 서울특별시교육청). 다른 종류에는 영향 없음") String org) {

    public static LawSearchRequest of(List<String> keywords, int limit, Integer perType) {
        return new LawSearchRequest(keywords, limit, null, perType, null, null);
    }
}
