package com.axedu.service.ai.keyword;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.axedu.service.ai.openai.OpenAiChatClient;

import lombok.extern.slf4j.Slf4j;

/** OPENAI_API_KEY 가 있으면 ChatGPT, 없으면 규칙 기반 대체 구현 */
@Slf4j
@Configuration
public class KeywordExtractorConfig {

    @Bean
    public KeywordExtractor keywordExtractor(OpenAiChatClient openAi, KeywordPrompt prompt) {
        if (openAi.enabled()) {
            log.info("민원 키워드 추출: OpenAI {}", openAi.model());
            return new OpenAiKeywordExtractor(openAi, prompt);
        }
        log.warn("OPENAI_API_KEY 가 없어 민원 키워드 추출을 규칙 기반 대체 구현으로 실행합니다 (.env 에 키를 추가하면 ChatGPT 사용)");
        return new RuleBasedKeywordExtractor();
    }
}
