package com.axedu;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.axedu.common.security.JwtTokenProvider;

/** POST /ai/keywords 응답 모양 (테스트에서는 OpenAI 키가 없어 규칙 기반 대체 구현) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
class AiKeywordApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtTokenProvider tokens;

    private String parent() {
        return "Bearer " + tokens.issue("usr_p1", "PARENT", "sch_demo", "", null);
    }

    @Test
    void extractsKeywordsWithRuleBasedFallback() throws Exception {
        mvc.perform(post("/api/axedu/v1/ai/keywords").header("Authorization", parent())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"폰을 뺏어가서 일주일째 안 돌려주세요\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.model").value("rule-based"))
                .andExpect(jsonPath("$.data.searchKeywords[0]").value("휴대전화"))
                .andExpect(jsonPath("$.data.searchKeywords[1]").value("압수"))
                .andExpect(jsonPath("$.data.keywords[0].evidence").isNotEmpty())
                .andExpect(jsonPath("$.data.category").value("LIFE_GUIDANCE"))
                .andExpect(jsonPath("$.data.laws").doesNotExist());
    }

    @Test
    void lawSearchUnavailableIsReportedInMeta() throws Exception {
        // 테스트 환경에는 파이썬 법령 조회 서버가 없을 수 있다 → laws 생략 + meta.lawSearch
        mvc.perform(post("/api/axedu/v1/ai/keywords").header("Authorization", parent())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"애들이 단톡방에서 왕따시켜요\",\"searchLaws\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.lawSearch").exists());
    }

    @Test
    void validatesText() throws Exception {
        mvc.perform(post("/api/axedu/v1/ai/keywords").header("Authorization", parent())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/axedu/v1/ai/keywords").header("Authorization", parent())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"" + "가".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
