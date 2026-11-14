package com.axedu;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/** 로컬 시드 데이터로 계약서의 응답 모양을 확인한다 (H2 메모리 DB, application-test.yml) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
class ApiContractTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtTokenProvider tokens;

    private String bearer(String userId, String role) {
        return "Bearer " + tokens.issue(userId, role, "sch_demo", "", null);
    }

    @Test
    void unauthorizedEnvelope() throws Exception {
        mvc.perform(get("/api/axedu/v1/chat/rooms"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void roomListHasCursorMeta() throws Exception {
        mvc.perform(get("/api/axedu/v1/chat/rooms").param("limit", "2").header("Authorization", bearer("usr_t1", "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].isWithinAvailableHours").isBoolean())
                .andExpect(jsonPath("$.meta.nextCursor").value("2"));
    }

    @Test
    void messagesNewestFirstWithHasMore() throws Exception {
        mvc.perform(get("/api/axedu/v1/chat/rooms/room_rh/messages").param("limit", "2").header("Authorization", bearer("usr_t1", "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("msg_rh_4"))
                .andExpect(jsonPath("$.data[1].id").value("msg_rh_3"))
                .andExpect(jsonPath("$.meta.hasMore").value(true))
                .andExpect(jsonPath("$.meta.nextCursor").value("msg_rh_3"));
    }

    @Test
    void teacherComplaintViewOmitsOriginalText() throws Exception {
        mvc.perform(get("/api/axedu/v1/complaints/cmp_seed1").header("Authorization", bearer("usr_t1", "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalText").doesNotExist())
                .andExpect(jsonPath("$.data.aiExplanation").doesNotExist())
                .andExpect(jsonPath("$.data.summaryCard.version").value(1))
                .andExpect(jsonPath("$.data.submittedAt").value("2026-11-13T20:14:00+09:00"));
        mvc.perform(get("/api/axedu/v1/complaints/cmp_seed1").header("Authorization", bearer("usr_p2", "PARENT")))
                .andExpect(jsonPath("$.data.originalText").isString())
                .andExpect(jsonPath("$.data.aiExplanation.recommendation").value("TALK_TO_TEACHER"));
    }

    @Test
    void validationErrorHasFields() throws Exception {
        mvc.perform(post("/api/axedu/v1/chat/rooms/room_rh/messages").header("Authorization", bearer("usr_t1", "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"c_x\",\"type\":\"TEXT\",\"content\":\" \",\"attachmentIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.fields.content").value("required"));
    }
}
