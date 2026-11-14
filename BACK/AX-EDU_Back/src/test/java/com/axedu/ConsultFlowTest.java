package com.axedu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

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
 * 똑똑 AI 상담사 (프론트 PR #4·#5) — 테스트에서는 OpenAI 키가 없어 규칙 기반으로 진행한다 (프론트 목과 같은 흐름).
 * 시드: usr_p2 의 자녀 usr_s5 는 3학년 9반 (담임 usr_t1 김수학 · 수학, 영어 usr_t2 이영어).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConsultFlowTest {

    private static final String API = "/api/axedu/v1/complaints";
    /** PR #5 의 확인 시나리오: 안전·사고 + 격한 첫 민원 */
    private static final String ANGRY_SAFETY = "체육 시간에 아이가 넘어져서 다쳤는데 연락 한 통 없었어요!! 이게 말이 됩니까? 교육청에 신고할 거예요.";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtTokenProvider tokens;

    @Autowired
    ObjectMapper mapper;

    private String bearer(String userId, String role) {
        return "Bearer " + tokens.issue(userId, role, "sch_demo", "", null);
    }

    private String parent;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        parent = bearer("usr_p2", "PARENT");
    }

    private JsonNode create(String content, String category) throws Exception {
        String cat = category == null ? "null" : "\"" + category + "\"";
        String body = mvc.perform(post(API).header("Authorization", parent).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"usr_s5\",\"content\":\"" + content + "\",\"category\":" + cat + ",\"attachmentIds\":[]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("data");
    }

    private static JsonNode last(JsonNode complaint) {
        JsonNode m = complaint.path("consultation");
        return m.get(m.size() - 1);
    }

    private static List<String> values(JsonNode quick) {
        List<String> out = new ArrayList<>();
        quick.forEach(q -> out.add(q.path("value").asText(q.path("label").asText())));
        return out;
    }

    @Test
    void fullConsultationFromAngrySafetyComplaintToTeacherCard() throws Exception {
        ConsultDriver d = new ConsultDriver(mvc, mapper, parent);

        // 1) 첫 반응: 공감 + '서둘러 살펴야' + 대응 시급도 질문 (PR #5)
        JsonNode c = create(ANGRY_SAFETY, "SAFETY");
        String id = c.path("id").asText();
        assertThat(c.path("consultStage").asText()).isEqualTo("FACTS");
        assertThat(c.path("category").asText()).isEqualTo("SAFETY");
        assertThat(c.path("teacher").isNull()).isTrue();
        JsonNode msgs = c.path("consultation");
        assertThat(msgs.size()).isEqualTo(3);
        assertThat(msgs.get(0).path("from").asText()).isEqualTo("PARENT");
        assertThat(msgs.get(1).path("content").asText()).contains("놀라고").contains("화가 나고 답답하신 게 당연해요");
        assertThat(msgs.get(2).path("content").asText()).contains("서둘러 살펴야 할 일일 수도").contains("얼마나 빨리 확인");
        assertThat(values(msgs.get(2).path("quickReplies"))).containsExactly("URGENT", "SOON", "NORMAL");

        // READY 전에는 카드를 만들 수 없다 (409 + details.stage)
        mvc.perform(post(API + "/" + id + "/summary-card").header("Authorization", parent))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.details.stage").value("FACTS"));

        // 2) 오늘 안에 → 112·119 안내 + 다음 질문(일시)
        c = d.send(id, "오늘 안에 꼭 확인이 필요해요", "URGENT");
        assertThat(c.path("consultation").get(4).path("content").asText()).contains("112·119").contains("언제 있었는지");

        // 3) 일시(빠른 답) → 장소('체육')와 자세한 이야기(첫 민원이 3문장)는 이미 채워져 건너뜀 → 아이 상태(자유 입력)
        c = d.send(id, "어제", null);
        assertThat(last(c).path("content").asText()).contains("어제 있었던 일이군요").contains("어떻게 지내고");
        c = d.send(id, "무릎이 까져서 아파하고 학교 가기 싫다고 해요", null);
        assertThat(last(c).path("content").asText()).contains("마음도 많이 무거우시겠어요").contains("어떻게 해 주면");
        // 4) 바라는 점(빠른 답) → EXPLAIN: 정리표(대응 시급도 맨 앞) + 규정·법령 설명 + 다음 행동
        c = d.send(id, "사실 확인과 설명", null);
        assertThat(c.path("consultStage").asText()).isEqualTo("EXPLAIN");
        JsonNode explainMsgs = c.path("consultation");
        JsonNode factsMsg = null;
        JsonNode explanationMsg = null;
        for (JsonNode m : explainMsgs) {
            if (m.has("facts")) {
                factsMsg = m;
            }
            if (m.has("explanation")) {
                explanationMsg = m;
            }
        }
        assertThat(factsMsg.path("facts").get(0).path("label").asText()).isEqualTo("대응 시급도");
        assertThat(factsMsg.path("facts").get(0).path("value").asText()).isEqualTo("긴급 · 오늘 안에 확인");
        assertThat(factsMsg.toString()).doesNotContain("마음 상태"); // 학부모 정리표에는 감정 상태 없음
        assertThat(explanationMsg.path("explanation").path("reason").asText()).contains("학교안전공제회");
        assertThat(c.path("aiExplanation").path("recommendation").asText()).isEqualTo("TALK_TO_TEACHER");
        assertThat(values(last(c).path("quickReplies"))).containsExactly("TO_TEACHER", "CLOSE", "MORE");

        // 5) 선생님께 전하기 → 추천 근거 + 담임(안전 주제) 추천, 선생님 빠른 답 value = teacherId
        c = d.send(id, "선생님께 전하고 싶어요", "TO_TEACHER");
        assertThat(c.path("consultStage").asText()).isEqualTo("RECIPIENT");
        JsonNode reasoning = c.path("consultation").get(c.path("consultation").size() - 2).path("reasoning");
        assertThat(reasoning.toString()).contains("안전에 관한 일은 담임 김수학 선생님");
        assertThat(last(c).path("quickReplies").get(0).path("label").asText()).isEqualTo("김수학 선생님 (추천)");
        assertThat(values(last(c).path("quickReplies"))).containsExactly("usr_t1", "usr_t2");

        // 6) 자유 입력으로 선생님 고르기('담임') → 연락 방법 → 시간 → READY
        c = d.send(id, "담임 선생님께 보내 주세요", null);
        assertThat(c.path("consultStage").asText()).isEqualTo("CONTACT");
        assertThat(c.path("proposedTeacher").path("id").asText()).isEqualTo("usr_t1");
        c = d.send(id, "안심전화", "CALL");
        assertThat(last(c).path("content").asText()).contains("안심전화로 연락받으시는 걸로");
        c = d.send(id, "오후 4~5시", "16:00-17:00");
        assertThat(c.path("consultStage").asText()).isEqualTo("READY");
        JsonNode readyFacts = c.path("consultation").get(c.path("consultation").size() - 2).path("facts");
        assertThat(readyFacts.toString()).contains("전할 선생님").contains("김수학 선생님").contains("안심전화, 16:00~17:00");

        // 받는 선생님 목록: 상담에서 정한 선생님이 추천
        mvc.perform(get(API + "/" + id + "/recipients").header("Authorization", parent))
                .andExpect(jsonPath("$.data[0].teacher.id").value("usr_t1"))
                .andExpect(jsonPath("$.data[0].recommended").value(true))
                .andExpect(jsonPath("$.data[0].reason").value("상담에서 함께 정한 선생님이에요."));

        // 7) 요약 카드: 긴급 + 사유, 학부모 응답에는 parentEmotion 없음, 신고 문장 제외, 상담 단계 종료
        mvc.perform(post(API + "/" + id + "/summary-card").header("Authorization", parent))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.urgency.level").value("URGENT"))
                .andExpect(jsonPath("$.data.urgency.reason").value("학생 안전·건강 관련"))
                .andExpect(jsonPath("$.data.parentEmotion").doesNotExist())
                .andExpect(jsonPath("$.data.basicInfo.tags[0]").value("SAFETY"))
                .andExpect(jsonPath("$.data.situation[0].when").value("어제"))
                .andExpect(jsonPath("$.data.preferredContact.methods[0]").value("CALL"))
                .andExpect(jsonPath("$.data.preferredContact.timeWindow.from").value("16:00"))
                .andExpect(jsonPath("$.data.requests[0]").value("사실관계 확인과 설명"))
                .andExpect(content().string(not(containsString("교육청"))))
                .andExpect(content().string(not(containsString("말이 됩니까"))));
        JsonNode drafting = d.get(id);
        assertThat(drafting.path("status").asText()).isEqualTo("CARD_DRAFTING");
        assertThat(drafting.path("consultStage").isNull()).isTrue();
        assertThat(drafting.path("summaryCard").has("parentEmotion")).isFalse();

        // 8) 직접 수정(PATCH)은 urgency 를 바꾸지 못하고 이어받는다. 말로 수정은 바꿀 수 있다
        mvc.perform(patch(API + "/" + id + "/summary-card").header("Authorization", parent).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseVersion\":1,\"concerns\":[\"무릎 상처 회복\"],\"urgency\":{\"level\":\"NORMAL\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.urgency.level").value("URGENT"));
        mvc.perform(post(API + "/" + id + "/summary-card/revisions").header("Authorization", parent).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseVersion\":2,\"instruction\":\"급하지 않아요\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(3))
                .andExpect(jsonPath("$.data.urgency.level").value("NORMAL"))
                .andExpect(jsonPath("$.data.urgency.reason").doesNotExist());
        mvc.perform(post(API + "/" + id + "/summary-card/revisions").header("Authorization", parent).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseVersion\":3,\"instruction\":\"긴급으로 바꿔 주세요\"}"))
                .andExpect(jsonPath("$.data.version").value(4))
                .andExpect(jsonPath("$.data.urgency.level").value("URGENT"));

        // 9) 제출 → 교사: 감정 상태는 보이고(격양됨, 신호 라벨만), 상담 내용·분류·원문은 보이지 않는다
        mvc.perform(post(API + "/" + id + "/submit").header("Authorization", parent).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardVersion\":4,\"teacherId\":\"usr_t1\"}"))
                .andExpect(status().isOk());
        mvc.perform(get(API + "/" + id).header("Authorization", bearer("usr_t1", "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summaryCard.urgency.level").value("URGENT"))
                .andExpect(jsonPath("$.data.summaryCard.parentEmotion.level").value("AGITATED"))
                .andExpect(jsonPath("$.data.summaryCard.parentEmotion.signals[0]").value("강한 불만 표현"))
                .andExpect(jsonPath("$.data.summaryCard.parentEmotion.summary").value(containsString("첫 연락은")))
                .andExpect(jsonPath("$.data.consultation").doesNotExist())
                .andExpect(jsonPath("$.data.consultStage").doesNotExist())
                .andExpect(jsonPath("$.data.category").doesNotExist())
                .andExpect(jsonPath("$.data.proposedTeacher").doesNotExist())
                .andExpect(jsonPath("$.data.originalText").doesNotExist())
                .andExpect(content().string(not(containsString("교육청"))))
                .andExpect(content().string(not(containsString("말이 됩니까"))));
    }

    @Test
    void firstReplyFailureThenRetry() throws Exception {
        String body = mvc.perform(post(API).header("Authorization", parent).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"usr_s5\",\"content\":\"[AI실패] 숙제 검사가 궁금해요\",\"attachmentIds\":[]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("AI_PROCESSING_FAILED"))
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(body).path("error").path("details").path("complaintId").asText();

        ConsultDriver d = new ConsultDriver(mvc, mapper, parent);
        JsonNode c = d.get(id);
        assertThat(c.path("consultStage").isNull()).isTrue();
        assertThat(c.path("consultation").size()).isEqualTo(1); // 학부모 첫 메시지는 저장됨

        // 첫 응답 전에는 상담을 이어 갈 수 없다 (422 + complaintId)
        mvc.perform(post(API + "/" + id + "/consultations").header("Authorization", parent).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"어제\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.details.complaintId").value(id));

        mvc.perform(post(API + "/" + id + "/explanation/retry").header("Authorization", parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consultStage").value("FACTS"));
    }

    @Test
    void validationAndCategory() throws Exception {
        mvc.perform(post(API).header("Authorization", parent).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"usr_s5\",\"content\":\"상담 부탁드려요\",\"category\":\"WEATHER\",\"attachmentIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.fields.category").value("invalid"));

        // 원문에 주제가 없으면 사전 분류(평가)를 따른다 → 평가 규칙의 공감 문구
        JsonNode c = create("상담 부탁드려요", "STUDY_EVALUATION");
        assertThat(c.path("consultation").get(1).path("content").asText()).contains("열심히 준비한 만큼");
        String id = c.path("id").asText();

        mvc.perform(post(API + "/" + id + "/consultations").header("Authorization", parent).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.fields.content").value("required"));
        mvc.perform(post(API + "/" + id + "/consultations").header("Authorization", bearer("usr_t1", "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"어제\"}"))
                .andExpect(status().isForbidden());

        // 상담 메시지 40개 상한
        ConsultDriver d = new ConsultDriver(mvc, mapper, parent);
        JsonNode cur = d.get(id);
        int guard = 0;
        while (cur.path("consultation").size() < 40 && guard++ < 40) {
            cur = d.send(id, "그리고 하나 더 말씀드리면 발표 순서도 마지막이었어요", null);
        }
        mvc.perform(post(API + "/" + id + "/consultations").header("Authorization", parent).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"또 있어요\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.fields.content").value("max turns"));

        // 학부모 목록에는 상담 단계·분류는 있고 대화 전체는 없다
        mvc.perform(get(API + "?status=EXPLAINED").header("Authorization", parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].consultation").doesNotExist())
                .andExpect(jsonPath("$.data[0].consultStage").exists());
    }
}
