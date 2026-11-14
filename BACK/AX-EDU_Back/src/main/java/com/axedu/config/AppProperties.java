package com.axedu.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * axedu.* 설정.
 *
 * @param cors     프론트 주소 (예: http://localhost:5173)
 * @param storage   파일 저장 위치·서명 URL 설정
 * @param lawSearch 법령 조회 파이썬 서버 (ai/law_search_server.py)
 * @param openai    OpenAI(ChatGPT) API — 민원 키워드 추출 등
 */
@ConfigurationProperties(prefix = "axedu")
public record AppProperties(Cors cors, Storage storage, LawSearch lawSearch, OpenAi openai) {

    public record Cors(List<String> allowedOrigins) {
    }

    /**
     * @param localDir      로컬 저장 폴더 (TODO: 운영은 오브젝트 스토리지)
     * @param publicBaseUrl 서명 URL 앞에 붙일 서버 주소 (예: http://localhost:8080)
     * @param urlTtlMinutes 서명 URL 유효 시간
     * @param signKey       서명 URL HMAC 키
     */
    public record Storage(String localDir, String publicBaseUrl, long urlTtlMinutes, String signKey) {
    }

    /**
     * @param baseUrl       법령 조회 서버 주소 (예: http://127.0.0.1:8090)
     * @param timeoutMillis 연결·응답 대기 시간
     */
    public record LawSearch(String baseUrl, int timeoutMillis) {
    }

    /**
     * @param apiKey        OpenAI API 키 (.env 의 OPENAI_API_KEY). 비어 있으면 AI 대신 규칙 기반 대체 구현을 쓴다
     * @param baseUrl       API 주소 (기본 https://api.openai.com/v1)
     * @param model         모델 이름 (예: gpt-4.1-mini)
     * @param timeoutMillis 응답 대기 시간
     * @param temperature   낮을수록 같은 입력에 같은 결과. gpt-5·o 시리즈 모델에는 보내지 않는다
     */
    public record OpenAi(String apiKey, String baseUrl, String model, int timeoutMillis, Double temperature) {

        public boolean enabled() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
