package com.axedu.service.ai.keyword;

/**
 * 민원 → 법령 검색 키워드 (AI 1단계).
 *
 * <pre>
 * 학부모 민원 → [키워드 추출] → 법령 조회(LawSearchClient) → (AI 2단계) 요약 카드 JSON → 프론트
 * </pre>
 *
 * 구현: OpenAiKeywordExtractor (OPENAI_API_KEY 가 있을 때), RuleBasedKeywordExtractor (키가 없을 때 대체)
 */
public interface KeywordExtractor {

    String modelName();

    /** @throws com.axedu.service.ai.AiException AI 호출 실패 */
    KeywordModels.Result extract(KeywordModels.Input input);
}
