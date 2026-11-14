package com.axedu;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.axedu.common.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 프론트 PR #4 학부모 민원 흐름 (AI 상담 제외): 방 없이 민원 시작 → 받을 선생님 목록 → 고른 선생님에게 제출(DIRECT 방 생성).
 * 시드: usr_p2 의 자녀 usr_s5 는 3학년 9반 (담임 usr_t1 · 수학, 영어 usr_t2). p2–t2 방은 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS) // 방·민원을 새로 만드므로 다른 테스트와 DB 를 나누지 않는다
class ParentComplaintFlowTest {

    private static final String API = "/api/axedu/v1";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtTokenProvider tokens;

    @Autowired
    ObjectMapper mapper;

    private String bearer(String userId, String role) {
        return "Bearer " + tokens.issue(userId, role, "sch_demo", "", null);
    }

    private JsonNode body(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private String createWithoutRoom(String parent, String student) throws Exception {
        JsonNode res = body(mvc.perform(post(API + "/complaints").header("Authorization", parent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"" + student + "\",\"content\":\"영어 수행평가 점수 기준을 알고 싶어요\",\"attachmentIds\":[]}"))
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("\"teacher\":null")))
                .andExpect(content().string(containsString("\"roomId\":null")))
                .andExpect(jsonPath("$.data.status").value("EXPLAINED")));
        return res.path("data").path("id").asText();
    }

    /** 상담을 READY 까지 진행(빠른 답)한 뒤 요약 카드 */
    private void makeCard(String parent, String id) throws Exception {
        new ConsultDriver(mvc, mapper, parent).toReady(id, "NORMAL", null);
        mvc.perform(post(API + "/complaints/" + id + "/summary-card").header("Authorization", parent))
                .andExpect(status().isCreated());
    }

    @Test
    void complaintWithoutRoomIsSubmittedToChosenTeacherInNewDirectRoom() throws Exception {
        String parent = bearer("usr_p2", "PARENT");
        String id = createWithoutRoom(parent, "usr_s5");

        // 제출 전에는 어떤 교사도 볼 수 없다
        mvc.perform(get(API + "/complaints/" + id).header("Authorization", bearer("usr_t2", "TEACHER")))
                .andExpect(status().isNotFound());

        // 받을 수 있는 선생님: 담임(추천) + 교과
        mvc.perform(get(API + "/complaints/" + id + "/recipients").header("Authorization", parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].teacher.id").value("usr_t1"))
                .andExpect(jsonPath("$.data[0].label").value("3학년 9반 담임 · 수학"))
                .andExpect(jsonPath("$.data[0].recommended").value(true))
                .andExpect(jsonPath("$.data[0].reason").isNotEmpty())
                .andExpect(jsonPath("$.data[1].teacher.id").value("usr_t2"))
                .andExpect(jsonPath("$.data[1].label").value("3학년 영어"))
                .andExpect(jsonPath("$.data[1].recommended").value(false))
                .andExpect(jsonPath("$.data[1].reason").doesNotExist());

        makeCard(parent, id);

        // teacherId 없음 / 후보 아님 → 400 details.fields.teacherId
        mvc.perform(post(API + "/complaints/" + id + "/submit").header("Authorization", parent)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cardVersion\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.fields.teacherId").value("required"));
        mvc.perform(post(API + "/complaints/" + id + "/submit").header("Authorization", parent)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cardVersion\":1,\"teacherId\":\"usr_rt1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.fields.teacherId").value("invalid"));

        // 영어 선생님에게 제출 → p2–t2 DIRECT 방 새로 생성
        JsonNode submitted = body(mvc.perform(post(API + "/complaints/" + id + "/submit").header("Authorization", parent)
                        .header("Idempotency-Key", "flow-submit-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cardVersion\":1,\"teacherId\":\"usr_t2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.teacher.id").value("usr_t2"))
                .andExpect(jsonPath("$.data.roomId").value(not("room_p2"))));
        String roomId = submitted.path("data").path("roomId").asText();

        // 같은 Idempotency-Key 재요청은 같은 결과
        mvc.perform(post(API + "/complaints/" + id + "/submit").header("Authorization", parent)
                        .header("Idempotency-Key", "flow-submit-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cardVersion\":1,\"teacherId\":\"usr_t2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roomId").value(roomId));

        // 고른 교사는 민원과 방(카드 메시지)을 보고, 담임은 못 본다. 교사 응답에는 원문이 없다
        String t2 = bearer("usr_t2", "TEACHER");
        mvc.perform(get(API + "/complaints/" + id).header("Authorization", t2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roomId").value(roomId))
                .andExpect(jsonPath("$.data.originalText").doesNotExist());
        mvc.perform(get(API + "/complaints/" + id).header("Authorization", bearer("usr_t1", "TEACHER")))
                .andExpect(status().isNotFound());
        mvc.perform(get(API + "/chat/rooms/" + roomId + "/messages").header("Authorization", t2))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("COMPLAINT_CARD")));
    }

    @Test
    void complaintStartedInRoomKeepsThatTeacher() throws Exception {
        String parent = bearer("usr_p1", "PARENT");
        JsonNode res = body(mvc.perform(post(API + "/complaints").header("Authorization", parent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":\"room_p1\",\"studentId\":\"usr_s1\",\"content\":\"휴대전화 압수 기간이 궁금합니다\",\"attachmentIds\":[]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.teacher.id").value("usr_t1"))
                .andExpect(jsonPath("$.data.roomId").value("room_p1")));
        String id = res.path("data").path("id").asText();

        mvc.perform(get(API + "/complaints/" + id + "/recipients").header("Authorization", parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].teacher.id").value("usr_t1"))
                .andExpect(jsonPath("$.data[0].recommended").value(true));

        makeCard(parent, id);
        mvc.perform(post(API + "/complaints/" + id + "/submit").header("Authorization", parent)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cardVersion\":1,\"teacherId\":\"usr_t2\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.fields.teacherId").value("invalid"));
        // teacherId 생략(이전 프론트 호환) → 방의 선생님에게
        mvc.perform(post(API + "/complaints/" + id + "/submit").header("Authorization", parent)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cardVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roomId").value("room_p1"));
    }

    @Test
    void recipientsOnlyForOwnerParent() throws Exception {
        String id = createWithoutRoom(bearer("usr_p2", "PARENT"), "usr_s5");
        mvc.perform(get(API + "/complaints/" + id + "/recipients").header("Authorization", bearer("usr_p1", "PARENT")))
                .andExpect(status().isNotFound());
        mvc.perform(get(API + "/complaints/" + id + "/recipients").header("Authorization", bearer("usr_t1", "TEACHER")))
                .andExpect(status().isForbidden());
    }
}
