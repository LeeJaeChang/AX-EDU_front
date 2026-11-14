package com.axedu.service.ai.keyword;

import com.axedu.service.ai.AiException;
import com.axedu.service.ai.openai.OpenAiChatClient;

/**
 * ChatGPT 로 민원 → 법령 검색 키워드.
 *
 * <p>프롬프트 엔지니어링 (prompts/keyword-extraction/):
 * <ul>
 *   <li>역할 부여: 교육 법령 검색 키워드 분석가, 판단·답변은 하지 않음</li>
 *   <li>배경 설명: 키워드가 '단어 시작 일치' 검색 엔진에 들어간다는 것과 검색 대상 자료 → 법령 용어를 쓰게 함</li>
 *   <li>단계적 사고: 출력 필드 순서를 상황 요약 → 법적 쟁점 → 키워드 → 분류로 두어 쟁점을 먼저 생각하게 함</li>
 *   <li>근거 연결: 키워드마다 원문 표현(evidence)을 쓰게 해 지어낸 키워드를 줄임</li>
 *   <li>용어 변환 표 + 검색 자료의 실제 용어 목록: 구어('폰 뺏겼다')를 검색되는 용어('휴대전화', '압수')로</li>
 *   <li>few-shot 3개: 생활지도·학교폭력·평가 + 인젝션 문장이 섞인 예시</li>
 *   <li>프롬프트 인젝션 방어: 민원을 &lt;complaint&gt; 태그로 감싸 '자료'로 취급, 태그 문자 무력화</li>
 *   <li>Structured Outputs(strict JSON 스키마) + 낮은 temperature: 형식 오류 없이 일관된 결과</li>
 *   <li>후처리(KeywordSanitizer): 일반어·개인정보·중복 제거, 최대 6개</li>
 * </ul>
 */
public class OpenAiKeywordExtractor implements KeywordExtractor {

    static final String SCHEMA_NAME = "complaint_law_keywords";
    static final int MAX_TOKENS = 700;

    private final OpenAiChatClient openAi;
    private final KeywordPrompt prompt;

    public OpenAiKeywordExtractor(OpenAiChatClient openAi, KeywordPrompt prompt) {
        this.openAi = openAi;
        this.prompt = prompt;
    }

    @Override
    public String modelName() {
        return openAi.model();
    }

    @Override
    public KeywordModels.Result extract(KeywordModels.Input input) {
        if (input == null || input.complaintText() == null || input.complaintText().isBlank()) {
            throw new AiException("EMPTY_INPUT", "민원 내용이 비어 있습니다.");
        }
        KeywordModels.LlmOutput out = openAi.structured(SCHEMA_NAME, prompt.schema(), prompt.messages(input), MAX_TOKENS,
                KeywordModels.LlmOutput.class);
        return new KeywordModels.Result(
                KeywordSanitizer.cut(out.situationSummary(), 200),
                KeywordSanitizer.issues(out.legalIssues()),
                KeywordSanitizer.keywords(out.keywords()),
                KeywordSanitizer.category(out.category()),
                openAi.model());
    }
}
