package com.axedu.service.consult;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.axedu.dto.complaint.AiExplanationResponse;
import com.axedu.dto.complaint.ConsultMessageResponse;
import com.axedu.entity.enums.AiRecommendation;
import com.axedu.entity.enums.ComplaintTag;
import com.axedu.entity.enums.ConsultStage;
import com.axedu.entity.value.ParentEmotion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 상담 엔진이 AI 해석을 쓰는 경로 + 감정 병합 규칙 + 프롬프트 스키마 (Spring 없이) */
class ConsultEngineAiTest {

    /** 가짜 컨텍스트: 선생님 2명, AI 해석은 함수로 주입 */
    static final class FakeCtx implements ConsultContext {
        Function<ConsultInterpretation.Request, ConsultInterpretation> ai = r -> null;
        final List<ConsultInterpretation.Request> asked = new ArrayList<>();
        int seq;

        @Override
        public List<Teacher> teachers() {
            return List.of(new Teacher("usr_t1", "김수학", true, List.of("수학")), new Teacher("usr_t2", "이영어", false, List.of("영어")));
        }

        @Override
        public String fixedTeacherId() {
            return null;
        }

        @Override
        public String teacherName(String id) {
            return "usr_t1".equals(id) ? "김수학" : "이영어";
        }

        @Override
        public AiExplanationResponse explain(List<String> cardTexts, ComplaintTag category) {
            return new AiExplanationResponse("공감", "설명", "대안", List.of(), AiRecommendation.TALK_TO_TEACHER);
        }

        @Override
        public Optional<ConsultInterpretation> interpret(ConsultInterpretation.Request request) {
            asked.add(request);
            return Optional.ofNullable(ai.apply(request));
        }

        @Override
        public String newMessageId() {
            return "cm_" + (seq++);
        }

        @Override
        public OffsetDateTime now() {
            return OffsetDateTime.parse("2026-11-14T10:00:00+09:00");
        }
    }

    static ConsultInterpretation interp(String ack, String when, String place, String impact, String request, String urgency,
                                        String teacherId, String emotion) {
        return new ConsultInterpretation(ack, when, place, impact, request, urgency, null, null, null, false, teacherId, emotion);
    }

    final ConsultEngine engine = new ConsultEngine();

    @Test
    void aiInterpretationFillsSlotsAndPersonalizesEmpathy() {
        FakeCtx ctx = new FakeCtx();
        ctx.ai = r -> r.pending() == null
                ? interp("발표 점수 때문에 아이가 속상해하니 마음이 많이 쓰이셨겠어요.", null, "영어 수업", null, null, null, null, "CONCERNED")
                : null;
        ConsultSession s = new ConsultSession();
        List<ConsultMessageResponse> out = engine.start("영어 발표 점수가 이상해요", s, ctx);

        assertThat(out.get(0).content()).startsWith("발표 점수 때문에 아이가 속상해하니");
        assertThat(s.slots().place).isEqualTo("영어 수업");
        assertThat(s.stage).isEqualTo(ConsultStage.FACTS);
        assertThat(ctx.asked.get(0).pending()).isNull();
    }

    @Test
    void freeTextUrgencyWhenAndTeacherChoiceUseAi() {
        FakeCtx ctx = new FakeCtx();
        ConsultSession s = new ConsultSession();
        engine.start("수행평가 점수가 이상해요", s, ctx);

        // 대응 시급도: 규칙으로는 SOON 이 될 말을 AI 가 URGENT 로 해석
        ctx.ai = r -> interp(null, null, null, null, null, "URGENT", null, "CONCERNED");
        engine.next("최대한 빨리요", null, s, ctx);
        assertThat(s.slots().urgency).isEqualTo("URGENT");
        assertThat(s.slots().urgencyReason).isEqualTo("보호자가 빠른 확인 요청");

        // 일시: AI 가 정리한 표현을 그대로
        ctx.ai = r -> interp(null, "지난주 금요일 3교시", "영어 수업", null, null, null, null, "CALM");
        engine.next("지난주 금욜 3교시 영어였어요", null, s, ctx);
        assertThat(s.slots().when).isEqualTo("지난주 금요일 3교시");
        assertThat(s.slots().place).isEqualTo("영어 수업");
        assertThat(ctx.asked.get(ctx.asked.size() - 1).pending()).isEqualTo("when");

        // 아이 상태: AI 공감 문장 사용
        engine.next("아이에게 더 들은 건 없어요", "UNKNOWN", s, ctx); // detail
        ctx.ai = r -> interp("시험 뒤로 의기소침해졌다니 걱정되시겠어요.", null, null, "아이가 의기소침해함", null, null, null, "CONCERNED");
        List<ConsultMessageResponse> out = engine.next("요즘 말이 없어졌어요", null, s, ctx);
        assertThat(out.get(0).content()).startsWith("시험 뒤로 의기소침해졌다니");
        assertThat(s.slots().impact).isEqualTo("아이가 의기소침해함");

        engine.next("사실 확인과 설명", null, s, ctx);
        assertThat(s.stage).isEqualTo(ConsultStage.EXPLAIN);
        out = engine.next("선생님께 전하고 싶어요", "TO_TEACHER", s, ctx);
        assertThat(s.stage).isEqualTo(ConsultStage.RECIPIENT);
        // 평가 주제 하나 + 영어 수업 → 과목 선생님 추천
        assertThat(out.get(1).quickReplies().get(0).label()).isEqualTo("이영어 선생님 (추천)");

        // 선생님 고르기: 이름이 없는 말이라도 AI 가 후보 중에서 고른 ID 로 (후보 목록과 추천을 AI 에 넘김)
        ctx.ai = r -> interp(null, null, null, null, null, null, "usr_t1", "CALM");
        engine.next("처음에 말씀드렸던 그분이요", null, s, ctx);
        assertThat(s.proposedTeacherId).isEqualTo("usr_t1");
        assertThat(ctx.asked.get(ctx.asked.size() - 1).teachers()).hasSize(2);
        assertThat(ctx.asked.get(ctx.asked.size() - 1).recommendedTeacherId()).isEqualTo("usr_t2");
        assertThat(s.stage).isEqualTo(ConsultStage.CONTACT);
    }

    @Test
    void recommendedPhraseAlwaysPicksRecommendedTeacher() {
        FakeCtx ctx = new FakeCtx();
        ConsultSession s = new ConsultSession();
        engine.start("아이가 체육 시간에 다쳤어요", s, ctx); // 안전 → 담임(usr_t1) 추천
        s.stage = ConsultStage.RECIPIENT;
        ctx.ai = r -> interp(null, null, null, null, null, null, "usr_t2", "CALM"); // AI 가 틀려도
        engine.next("아까 추천해 주신 분께 보내 주세요", null, s, ctx);
        assertThat(s.proposedTeacherId).isEqualTo("usr_t1");
    }

    @Test
    void urgencyAnswerDoesNotFillOtherSlots() {
        FakeCtx ctx = new FakeCtx();
        ConsultSession s = new ConsultSession();
        engine.start("숙제 검사가 궁금해요", s, ctx);
        engine.next("오늘 안에 꼭 확인해 주셨으면 해요", null, s, ctx);
        assertThat(s.slots().urgency).isEqualTo("URGENT");
        assertThat(s.slots().request).isNull();
        assertThat(s.secret.parentSaid).contains("오늘 안에 꼭 확인해 주셨으면 해요");
    }

    @Test
    void aiTeacherIdOutsideCandidatesIsIgnored() {
        FakeCtx ctx = new FakeCtx();
        ConsultSession s = new ConsultSession();
        s.stage = ConsultStage.RECIPIENT;
        ctx.ai = r -> interp(null, null, null, null, null, null, "usr_hacker", "CALM");
        List<ConsultMessageResponse> out = engine.next("아무나요", null, s, ctx);
        assertThat(s.proposedTeacherId).isNull();
        assertThat(out.get(0).content()).contains("한 분을 골라");
    }

    @Test
    void quickRepliesNeverCallAi() {
        FakeCtx ctx = new FakeCtx();
        ConsultSession s = new ConsultSession();
        engine.start("휴대전화를 가져가셨어요", s, ctx);
        int before = ctx.asked.size();
        engine.next("급하지 않아요", "NORMAL", s, ctx);
        engine.next("어제", null, s, ctx);
        assertThat(ctx.asked).hasSize(before);
        assertThat(s.secret.parentSaid).hasSize(1); // 빠른 답은 감정 판단에서 제외
    }

    @Test
    void agitatedTextGetsAcknowledgedOnce() {
        FakeCtx ctx = new FakeCtx();
        ConsultSession s = new ConsultSession();
        engine.start("숙제 검사가 궁금해요", s, ctx);
        List<ConsultMessageResponse> out = engine.next("이게 말이 됩니까?? 교육청에 신고할 거예요", null, s, ctx);
        assertThat(out.get(0).content()).isEqualTo(ConsultEngine.AGITATED_ACK);
        out = engine.next("정말 어이없네요!!", null, s, ctx);
        assertThat(out.stream().map(ConsultMessageResponse::content)).doesNotContain(ConsultEngine.AGITATED_ACK);
    }

    @Test
    void emotionMergeKeepsRuleFloorAndKnownLabels() {
        ParentEmotion rule = ConsultText.assessEmotion(List.of("교육청에 신고할 거예요"));
        assertThat(rule.level()).isEqualTo("AGITATED");
        ParentEmotion ai = new ParentEmotion("CALM", "EASING", List.of("불안·걱정 표현", "지어낸 라벨"), "차분하셨어요.");
        ParentEmotion merged = ConsultCardWriter.mergeEmotion(rule, ai);
        assertThat(merged.level()).isEqualTo("AGITATED");
        assertThat(merged.signals()).containsExactly("책임 추궁·외부 신고 언급", "불안·걱정 표현");
        assertThat(merged.trend()).isEqualTo("STEADY"); // 흐름은 규칙(첫 vs 마지막 메시지)으로
        assertThat(merged.summary()).contains("첫 연락은"); // AI 요약이 수준과 안 맞으면 규칙 요약
    }

    @Test
    void consultPromptSchemasAreStrictModeCompatible() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        for (String dir : List.of("consult-turn", "consult-explain", "consult-card")) {
            try (InputStream in = new ClassPathResource("prompts/" + dir + "/schema.json").getInputStream()) {
                assertStrict(mapper.readTree(in), dir);
            }
            assertThat(new ClassPathResource("prompts/" + dir + "/system.md").exists()).isTrue();
        }
    }

    private static void assertStrict(JsonNode node, String path) {
        if (node.path("type").toString().contains("object")) {
            Set<String> props = new HashSet<>();
            node.path("properties").fieldNames().forEachRemaining(props::add);
            Set<String> required = new HashSet<>();
            node.path("required").forEach(r -> required.add(r.asText()));
            assertThat(required).as(path + " required").isEqualTo(props);
            assertThat(node.path("additionalProperties").asBoolean(true)).as(path + " additionalProperties").isFalse();
            node.path("properties").properties().forEach(e -> assertStrict(e.getValue(), path + "." + e.getKey()));
        }
        if (node.has("items")) {
            assertStrict(node.path("items"), path + "[]");
        }
    }
}
