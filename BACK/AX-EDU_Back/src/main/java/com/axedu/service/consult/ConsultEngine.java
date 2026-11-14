package com.axedu.service.consult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.axedu.dto.chat.TimeRange;
import com.axedu.dto.complaint.AiExplanationResponse;
import com.axedu.dto.complaint.ConsultMessageResponse;
import com.axedu.dto.complaint.ConsultMessageResponse.Fact;
import com.axedu.dto.complaint.ConsultMessageResponse.QuickReply;
import com.axedu.entity.enums.ComplaintTag;
import com.axedu.entity.enums.ConsultStage;
import com.axedu.entity.enums.UrgencyLevel;

/**
 * 똑똑 AI 상담사의 진행 규칙 (프론트 목 mockConsult.ts 와 같은 흐름).
 *
 * <pre>
 * 첫 민원 → 공감 + 첫 질문 → FACTS(대응 시급도 → 일시 → 장소 → 자세한 이야기 → 아이 상태 → 바라는 점)
 * → EXPLAIN(사실관계 정리표 + 규정·법령 설명) → RECIPIENT(연락할 선생님 추천) → CONTACT(방법·시간) → READY
 * </pre>
 *
 * 단계 진행·슬롯 채우기·빠른 답은 서버 규칙으로 정해 화면이 안정적이고, 학부모가 자유롭게 쓴 말의 해석과
 * 공감 문장은 AI(ctx.interpret)가 맡는다. AI 가 꺼져 있거나 실패하면 같은 자리를 규칙(정규식)이 채운다.
 */
@Component
public class ConsultEngine {

    // ---------------- 말 뽑기 ----------------

    static final Pattern WHEN_RE = Pattern.compile(
            "((?:지난주|이번 ?주|지난달)\\s*[월화수목금]요일|\\d{1,2}월 ?\\d{1,2}일\\s*\\d교시|오늘|어제|그저께|그제|지난주|이번 ?주|지난달|\\d{1,2}월 ?\\d{1,2}일|[월화수목금]요일|\\d교시|아침|점심시간|방과 ?후|등교|하교)");
    static final Pattern PLACE_RE = Pattern.compile(
            "(수학|영어|국어|과학|사회|체육관|체육(?!복|관)|음악|미술|조회|종례|담임 ?시간|쉬는 ?시간|점심시간|교실|운동장|급식실|복도|체육관|야간 ?자율학습|야자|자율학습|학원)");
    static final Pattern SUBJECT_RE = Pattern.compile("^(수학|영어|국어|과학|사회|체육|음악|미술)$");
    static final Pattern IMPACT_RE = Pattern.compile(ConsultText.CONCERN.pattern() + "|괜찮아|싫어해|싫다고|무서워|울었|울면서|울어|당황|기운이 없");
    /** '설명해 주지 않았대요'처럼 있었던 일을 말하는 문장은 요청으로 보지 않는다 */
    static final Pattern REQ_RE = Pattern.compile(ConsultText.REQUEST.pattern() + "|사과를|재발|싶어요|싶습니다");

    /** 예: '지난주 금요일' + '3교시' → '지난주 금요일 3교시' */
    static String merge(String old, String next, Pattern addOn) {
        if (next == null || next.equals(old)) {
            return old;
        }
        if (old == null || old.equals("확인 필요")) {
            return next;
        }
        return addOn.matcher(next).find() && !addOn.matcher(old).find() ? old + " " + next : next;
    }

    private static String firstGroup(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static String sentenceWith(String text, Pattern re) {
        return ConsultText.sentencesOf(text).stream().filter(x -> re.matcher(x).find()).findFirst().orElse(null);
    }

    /** 자유롭게 쓴 말에서 아직 비어 있는 칸을 채운다 (규칙) */
    static void extract(String text, ConsultSession cs) {
        ConsultSession.Slots s = cs.slots();
        String when = firstGroup(WHEN_RE, text);
        if (when != null && s.when == null) {
            s.when = when;
        }
        String place = firstGroup(PLACE_RE, text);
        if (place != null && s.place == null) {
            s.place = SUBJECT_RE.matcher(place).matches() ? place + " 수업" : place;
        }
        String impact = sentenceWith(text, IMPACT_RE);
        if (impact != null && s.impact == null) {
            s.impact = ConsultText.soften(impact);
        }
        String request = sentenceWith(text, REQ_RE);
        if (request != null && s.request == null) {
            s.request = ConsultText.soften(request);
        }
        List<String> methods = ConsultText.parseMethods(text);
        if (methods != null && Pattern.compile("연락|전화|통화|문자|면담|방문").matcher(text).find() && s.methods == null) {
            s.methods = methods;
        }
        TimeRange tw = ConsultText.parseTimeWindow(text);
        if (tw != null && !s.timeAnswered) {
            s.timeWindow = tw;
            s.timeAnswered = true;
        }
    }

    /** AI 해석으로 비어 있는 칸을 채운다 (이미 찬 칸은 그대로) */
    static void apply(ConsultInterpretation in, ConsultSession cs) {
        apply(in, cs, null, null);
    }

    /**
     * AI 해석으로 비어 있는 칸을 채운다. 아이 상태·바라는 점은 AI 가 짐작해 채우기 쉬워서
     * 그 질문을 하는 중이거나 말에 그런 표현(규칙)이 있을 때만 받는다 → 상담이 질문을 건너뛰지 않게.
     */
    static void apply(ConsultInterpretation in, ConsultSession cs, String pending, String text) {
        if (in == null) {
            return;
        }
        boolean impactOk = text == null || "impact".equals(pending) || IMPACT_RE.matcher(text).find();
        boolean requestOk = text == null || "request".equals(pending) || REQ_RE.matcher(text).find();
        ConsultSession.Slots s = cs.slots();
        if (s.when == null && present(in.when())) {
            s.when = clip(in.when(), 30);
        }
        if (s.place == null && present(in.place())) {
            s.place = clip(in.place(), 30);
        }
        if (s.impact == null && impactOk && present(in.impact())) {
            s.impact = clip(ConsultText.soften(in.impact()), 120);
        }
        if (s.request == null && requestOk && present(in.request())) {
            s.request = clip(ConsultText.soften(in.request()), 120);
        }
        if (s.methods == null && in.methods() != null) {
            List<String> m = in.methods().stream().filter(x -> x != null && x.matches("MESSAGE|CALL|VISIT")).distinct().toList();
            if (!m.isEmpty()) {
                s.methods = new ArrayList<>(m);
            }
        }
        if (!s.timeAnswered) {
            TimeRange tw = timeOf(in);
            if (tw != null) {
                s.timeWindow = tw;
                s.timeAnswered = true;
            }
        }
    }

    static TimeRange timeOf(ConsultInterpretation in) {
        if (in == null || in.timeFrom() == null || in.timeTo() == null) {
            return null;
        }
        if (!in.timeFrom().matches("\\d{2}:\\d{2}") || !in.timeTo().matches("\\d{2}:\\d{2}") || in.timeFrom().compareTo(in.timeTo()) >= 0) {
            return null;
        }
        return new TimeRange(in.timeFrom(), in.timeTo());
    }

    private static boolean present(String s) {
        return s != null && !s.isBlank() && !s.equalsIgnoreCase("null");
    }

    private static String clip(String s, int max) {
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    // ---------------- 질문 ----------------

    record Question(String content, String note, List<QuickReply> quick) {
    }

    static final List<QuickReply> URGENCY_QUICK = List.of(
            new QuickReply("오늘 안에 꼭 확인이 필요해요", "URGENT"),
            new QuickReply("며칠 안에 답을 받으면 돼요", "SOON"),
            new QuickReply("급하지 않아요", "NORMAL"));

    static final Map<String, Question> QUESTIONS = new LinkedHashMap<>();

    static {
        QUESTIONS.put("urgency", new Question("먼저 하나 여쭤볼게요. 이 일은 선생님이 얼마나 빨리 확인해 주시면 좋을까요?",
                "아이의 안전이나 건강이 걸린 일이면 '오늘 안에'를 골라 주세요. 카드에 '긴급'으로 표시해 선생님께 먼저 알려 드려요.",
                URGENCY_QUICK));
        QUESTIONS.put("when", new Question("괜찮으시다면, 그 일이 언제 있었는지 여쭤봐도 될까요?",
                "날짜나 교시를 알면 선생님이 당시 기록을 찾아 사실을 확인하기 쉬워요.",
                List.of(QuickReply.of("오늘"), QuickReply.of("어제"), QuickReply.of("이번 주"), QuickReply.of("지난주"),
                        new QuickReply("잘 기억나지 않아요", "UNKNOWN"))));
        QUESTIONS.put("place", new Question("그때가 어느 수업 시간이었는지, 혹은 어디에서 있었던 일인지 기억나세요?",
                "수업 중이었다면 그 수업을 맡은 선생님이 상황을 가장 잘 알고 계세요.",
                List.of(QuickReply.of("조회·종례 시간"), QuickReply.of("수학 수업"), QuickReply.of("영어 수업"), QuickReply.of("쉬는 시간"),
                        new QuickReply("잘 모르겠어요", "UNKNOWN"))));
        QUESTIONS.put("detail", new Question("아이에게 들으신 이야기를 조금 더 들려주실 수 있을까요? 천천히 말씀하셔도 괜찮아요.",
                "선생님이 어떤 말이나 조치를 했는지, 아이는 어떻게 반응했는지가 사실관계를 확인하는 데 중요해요.",
                List.of(new QuickReply("아이에게 더 들은 건 없어요", "UNKNOWN"))));
        QUESTIONS.put("impact", new Question("이 일이 있고 나서 아이는 좀 어떻게 지내고 있나요?",
                "아이의 지금 마음 상태도 선생님께 함께 전해 드리면 아이를 살피는 데 도움이 돼요.",
                List.of(QuickReply.of("많이 속상해해요"), QuickReply.of("학교 가기 싫어해요"), QuickReply.of("겉으로는 괜찮아 보여요"))));
        QUESTIONS.put("request", new Question("보호자님께서는 학교가 어떻게 해 주면 마음이 좀 놓이실 것 같으세요?",
                "바라시는 점이 분명하면 선생님도 무엇을 준비해서 답해야 할지 알 수 있어요.",
                List.of(QuickReply.of("사실 확인과 설명"), QuickReply.of("재발 방지 약속"), QuickReply.of("아이에 대한 관심과 지도"),
                        QuickReply.of("일단 상황만 알리고 싶어요"))));
    }

    /** 빠른 답 문구 (감정 판단·AI 해석에서 제외) */
    static final Set<String> QUICK_LABELS = QUESTIONS.values().stream()
            .flatMap(q -> q.quick().stream()).map(QuickReply::label).collect(Collectors.toSet());

    static final Map<String, String> REQUEST_LABEL = Map.of(
            "사실 확인과 설명", "사실관계 확인과 설명",
            "재발 방지 약속", "같은 일이 반복되지 않도록 방안 안내",
            "아이에 대한 관심과 지도", "학생에 대한 관심과 지도",
            "일단 상황만 알리고 싶어요", "상황 공유 (별도 조치 요청 없음)");

    static final Map<String, String> METHOD_LABEL = Map.of("MESSAGE", "채팅 메시지", "CALL", "안심전화", "VISIT", "방문 면담");

    public static final Map<String, String> URGENCY_LABEL = Map.of(
            "URGENT", "긴급 · 오늘 안에 확인", "SOON", "며칠 안에 답변", "NORMAL", "급하지 않음");

    static final Map<String, String> URGENCY_ACK = Map.of(
            "URGENT", "알겠어요. 카드에 '긴급'으로 표시해서 선생님이 가장 먼저 보시도록 할게요.\n"
                    + "혹시 지금 아이가 위험한 상황이라면 기다리지 마시고 112·119나 학교 교무실로 바로 연락해 주세요.",
            "SOON", "네, 며칠 안에 답을 받으실 수 있도록 전해 둘게요.",
            "NORMAL", "네, 차분히 확인해도 되는 일로 전해 둘게요.");

    static final List<QuickReply> ACTIONS_AFTER_EXPLAIN = List.of(
            new QuickReply("선생님께 전하고 싶어요", "TO_TEACHER"),
            new QuickReply("설명으로 충분해요", "CLOSE"),
            new QuickReply("정리한 내용을 고치고 싶어요", "MORE"));

    static final String AGITATED_ACK = "많이 화가 나고 답답하신 마음, 충분히 이해해요. 천천히 하나씩 함께 정리해 볼게요.";

    // ---------------- 메시지 만들기 ----------------

    private static ConsultMessageResponse ai(ConsultContext ctx, String content, String note, List<Fact> facts,
                                            AiExplanationResponse explanation, List<String> reasoning, List<QuickReply> quick) {
        return new ConsultMessageResponse(ctx.newMessageId(), "AI", content, note, facts, explanation, reasoning, quick, null, ctx.now());
    }

    private static ConsultMessageResponse ai(ConsultContext ctx, String content) {
        return ai(ctx, content, null, null, null, null, null);
    }

    private static ConsultMessageResponse ask(ConsultContext ctx, ConsultSession cs, String key, String lead) {
        cs.secret.pending = key;
        Question q = QUESTIONS.get(key);
        String content = lead == null || lead.isBlank() ? q.content() : lead + "\n" + q.content();
        return ai(ctx, content, q.note(), null, null, null, q.quick());
    }

    List<Fact> factsOf(ConsultSession cs, ConsultContext ctx, boolean withContact) {
        ConsultSession.Slots s = cs.slots();
        String what = cs.secret.cardTexts.stream()
                .map(t -> ConsultText.soften(ConsultText.sentencesOf(t).stream().findFirst().orElse(t)))
                .filter(x -> !x.isBlank())
                .collect(Collectors.joining(" "));
        List<Fact> facts = new ArrayList<>();
        facts.add(new Fact("대응 시급도", s.urgency == null ? "확인 필요" : URGENCY_LABEL.getOrDefault(s.urgency, s.urgency)));
        facts.add(new Fact("일시", s.when == null ? "확인 필요" : s.when));
        facts.add(new Fact("장소·수업", s.place == null ? "확인 필요" : s.place));
        facts.add(new Fact("있었던 일", what.length() > 90 ? what.substring(0, 90) + "…" : what));
        facts.add(new Fact("아이 상태", s.impact == null ? "—" : s.impact));
        facts.add(new Fact("바라는 점", s.request == null ? "—" : s.request));
        if (withContact) {
            if (cs.proposedTeacherId != null) {
                facts.add(new Fact("전할 선생님", ctx.teacherName(cs.proposedTeacherId) + " 선생님"));
            }
            String methods = s.methods == null || s.methods.isEmpty() ? "채팅 메시지"
                    : s.methods.stream().map(m -> METHOD_LABEL.getOrDefault(m, m)).collect(Collectors.joining("·"));
            String time = s.timeWindow == null ? "시간 무관" : s.timeWindow.from() + "~" + s.timeWindow.to();
            facts.add(new Fact("희망 연락", methods + ", " + time));
            // 학부모 마음 상태는 교사용 카드에만 담기므로 학부모에게 보이는 정리표에는 넣지 않는다
        }
        return facts;
    }

    private List<ConsultMessageResponse> explainStage(ConsultContext ctx, ConsultSession cs) {
        cs.stage = ConsultStage.EXPLAIN;
        cs.secret.pending = null;
        AiExplanationResponse explanation = ctx.explain(cs.secret.cardTexts, cs.category);
        List<ConsultMessageResponse> out = new ArrayList<>();
        out.add(ai(ctx, "차근차근 말씀해 주셔서 고마워요. 들려주신 이야기를 사실관계로 정리해 봤어요.",
                "제가 잘못 이해한 부분이 있으면 편하게 고쳐 주세요.", factsOf(cs, ctx, false), null, null, null));
        out.add(ai(ctx, "그리고 이 일과 관련된 학교 규정과 법령을 찾아봤어요.", null, null, explanation, null, null));
        out.add(ai(ctx, "설명을 들으시고 마음이 좀 어떠세요?\n궁금하셨던 점이 풀리셨다면 여기서 마무리해도 괜찮아요. "
                + "그래도 선생님과 직접 이야기 나누고 싶으시면, 누구에게 전하면 좋을지 같이 정해 볼게요.", null, null, null, null,
                ACTIONS_AFTER_EXPLAIN));
        return out;
    }

    record Reasoning(List<String> bullets, String pick) {
    }

    /** 누구에게 연락할지: 상담 내용과 규정의 소관을 근거로 추론한다 */
    Reasoning recipientReasoning(ConsultSession cs, ConsultContext ctx) {
        List<ConsultContext.Teacher> teachers = ctx.teachers();
        String joined = String.join(" ", cs.secret.cardTexts) + " " + (cs.slots().place == null ? "" : cs.slots().place);
        List<ConsultRules.Rule> rules = ConsultRules.rulesOf(cs.secret.cardTexts, cs.category);
        Set<ComplaintTag> tags = rules.stream().map(ConsultRules.Rule::tag).collect(Collectors.toSet());
        ConsultContext.Teacher homeroom = teachers.stream().filter(ConsultContext.Teacher::homeroom).findFirst()
                .orElse(teachers.isEmpty() ? null : teachers.get(0));
        ConsultContext.Teacher subject = teachers.stream()
                .filter(t -> homeroom == null || !t.id().equals(homeroom.id()))
                .filter(t -> t.subjects().stream().anyMatch(sub -> !sub.isBlank() && joined.contains(sub)))
                .findFirst().orElse(null);
        String homeroomName = homeroom == null ? "담임" : homeroom.name();
        List<String> bullets = new ArrayList<>();

        if (subject != null) {
            String sub = subject.subjects().stream().filter(joined::contains).findFirst().orElse(subject.subjects().get(0));
            bullets.add(sub + " 시간에 있었던 일이라, 수업을 맡은 " + subject.name() + " 선생님이 당시 상황을 가장 잘 알고 계세요.");
        }
        if (tags.contains(ComplaintTag.LIFE_GUIDANCE)) {
            bullets.add("휴대전화 보관·복장·출결 같은 생활지도는 담임 " + homeroomName + " 선생님이 맡고 계세요.");
        }
        if (tags.contains(ComplaintTag.PEER_RELATION) || tags.contains(ComplaintTag.SAFETY)) {
            bullets.add("친구 관계나 안전에 관한 일은 담임 " + homeroomName + " 선생님이 먼저 살피고, 필요하면 학교 차원에서 함께 확인해요.");
        }
        if (tags.contains(ComplaintTag.STUDY_EVALUATION) && subject == null) {
            bullets.add("평가에 관한 일은 그 과목 선생님이 채점 기준을 알고 계세요. 어느 과목인지 알려 주시면 더 정확히 찾아 드릴게요.");
        }
        if (rules.size() > 1) {
            bullets.add("여러 일이 함께 있어서, 담임선생님이 먼저 전체를 보시고 필요하면 교과 선생님과 나누시는 게 빨라요.");
        }
        if (bullets.isEmpty()) {
            bullets.add("아이의 학교생활 전반은 담임 " + homeroomName + " 선생님이 가장 잘 알고 계세요.");
        }

        boolean studyOnly = rules.stream().allMatch(r -> r.tag() == ComplaintTag.STUDY_EVALUATION);
        String pick = subject != null && studyOnly && rules.size() == 1 ? subject.id() : homeroom == null ? null : homeroom.id();
        if (subject != null && homeroom != null && homeroom.id().equals(pick) && tags.contains(ComplaintTag.LIFE_GUIDANCE)) {
            bullets.add("이번 일의 중심은 생활지도라서, 담임 " + homeroomName + " 선생님께 먼저 전하시고 필요하면 " + subject.name()
                    + " 선생님과 함께 확인하시는 게 좋겠어요.");
        }
        if (subject != null && subject.id().equals(pick)) {
            bullets.add("채점 기준과 감점 사유는 " + subject.name() + " 선생님이 직접 설명해 주실 수 있어요.");
        }
        return new Reasoning(bullets, pick);
    }

    List<QuickReply> recipientQuick(String pick, ConsultContext ctx) {
        List<ConsultContext.Teacher> sorted = new ArrayList<>(ctx.teachers());
        sorted.sort((a, b) -> Boolean.compare(b.id().equals(pick), a.id().equals(pick)));
        return sorted.stream().map(t -> new QuickReply(t.name() + " 선생님" + (t.id().equals(pick) ? " (추천)" : ""), t.id())).toList();
    }

    private List<ConsultMessageResponse> recipientStage(ConsultContext ctx, ConsultSession cs) {
        String fixed = ctx.fixedTeacherId();
        if (fixed != null) {
            cs.proposedTeacherId = fixed;
            return contactStage(ctx, cs, ctx.teacherName(fixed) + " 선생님께 전해 드릴게요.");
        }
        if (ctx.teachers().isEmpty()) {
            // 담임·교과 정보가 없으면 받는 선생님은 카드 단계에서 고른다
            return contactStage(ctx, cs, "전할 선생님은 요약 카드를 보낼 때 고르실 수 있어요.");
        }
        cs.stage = ConsultStage.RECIPIENT;
        cs.secret.pending = null;
        Reasoning r = recipientReasoning(cs, ctx);
        return List.of(
                ai(ctx, "그럼 이 이야기를 누구에게 전하면 좋을지 같이 생각해 볼게요.", null, null, null, r.bullets(), null),
                ai(ctx, "제 생각에는 " + ctx.teacherName(r.pick()) + " 선생님께 먼저 전하시는 게 좋겠어요. 보호자님 생각은 어떠세요?",
                        "다른 선생님이 더 편하시면 그분을 골라 주셔도 괜찮아요.", null, null, null, recipientQuick(r.pick(), ctx)));
    }

    private List<ConsultMessageResponse> contactStage(ConsultContext ctx, ConsultSession cs, String lead) {
        cs.stage = ConsultStage.CONTACT;
        ConsultSession.Slots s = cs.slots();
        if (s.methods == null) {
            cs.secret.pending = "method";
            return List.of(ai(ctx, lead + "\n선생님이 답을 드릴 때 어떤 방법이 편하세요?",
                    "안심전화는 개인 번호를 드러내지 않고 통화할 수 있어요.", null, null, null,
                    List.of(new QuickReply("채팅 메시지", "MESSAGE"), new QuickReply("안심전화", "CALL"), new QuickReply("방문 면담", "VISIT"))));
        }
        if (!s.timeAnswered) {
            return List.of(askTime(ctx, cs, lead));
        }
        return readyStage(ctx, cs, lead);
    }

    private ConsultMessageResponse askTime(ConsultContext ctx, ConsultSession cs, String lead) {
        cs.secret.pending = "time";
        return ai(ctx, (lead == null ? "" : lead + "\n") + "연락받기 편한 시간대가 있으실까요?",
                "선생님의 대화 가능 시간 안에서 맞춰 드릴게요.", null, null, null,
                List.of(new QuickReply("오전 9~12시", "09:00-12:00"), new QuickReply("점심 12~13시", "12:00-13:00"),
                        new QuickReply("오후 4~5시", "16:00-17:00"), new QuickReply("언제든 괜찮아요", "ANY")));
    }

    private List<ConsultMessageResponse> readyStage(ConsultContext ctx, ConsultSession cs, String lead) {
        cs.stage = ConsultStage.READY;
        cs.secret.pending = null;
        return List.of(
                ai(ctx, (lead == null ? "" : lead + "\n") + "오늘 이야기 나눠 주셔서 정말 고마워요. 지금까지 함께 정리한 내용이에요.",
                        null, factsOf(cs, ctx, true), null, null, null),
                ai(ctx, "이 내용으로 선생님께 드릴 요약 카드를 만들어 볼까요?",
                        "카드에는 감정 표현은 덜고 있었던 일과 바라는 점 위주로 담겨요. 보내기 전에 보호자님이 확인하고 고치실 수 있어요.",
                        null, null, null, List.of(new QuickReply("요약 카드 만들기", "MAKE_CARD"))));
    }

    // ---------------- 진입점 ----------------

    /** 첫 민원에 대한 상담사의 첫 반응: 공감 → 상담 방식 안내 → 첫 질문 (대응 시급도) */
    public List<ConsultMessageResponse> start(String text, ConsultSession cs, ConsultContext ctx) {
        cs.stage = ConsultStage.FACTS;
        cs.secret.cardTexts = new ArrayList<>(List.of(text));
        cs.secret.parentSaid = new ArrayList<>(List.of(text));
        Optional<ConsultInterpretation> in = ctx.interpret(new ConsultInterpretation.Request(null, null, text, null, List.of(), null));
        in.ifPresent(x -> apply(x, cs, null, text));
        extract(text, cs);
        if (ConsultText.sentencesOf(text).size() >= 3) {
            cs.slots().detail = "given";
        }

        ConsultRules.Rule rule = ConsultRules.rulesOf(List.of(text), cs.category).get(0);
        boolean agitated = "AGITATED".equals(ConsultText.assessEmotion(List.of(text)).level())
                || in.map(x -> "AGITATED".equals(x.emotion())).orElse(false);
        cs.emotionAcked = agitated;
        String feelings = agitated || ConsultText.hasEmotion(text) ? "그런 일을 겪으시면 화가 나고 답답하신 게 당연해요. " : "";
        String empathy = in.map(ConsultInterpretation::ack).filter(ConsultEngine::present).orElse(rule.empathy());
        // 빠른 대응 여부는 언제나 가장 먼저 확인한다 (안전·건강 표현이 보이면 그 점을 짚는다)
        boolean hint = ConsultText.detectUrgency(text) == UrgencyLevel.URGENT || in.map(x -> "URGENT".equals(x.urgency())).orElse(false);
        String lead = hint
                ? "말씀을 들어 보니 서둘러 살펴야 할 일일 수도 있겠어요. 보호자님 이야기를 제대로 이해하고 도와드릴 수 있도록, 몇 가지만 하나씩 여쭤볼게요."
                : "보호자님 이야기를 제대로 이해하고 도와드릴 수 있도록, 몇 가지만 하나씩 여쭤볼게요.";
        String next = cs.nextEmpty();
        return List.of(
                ai(ctx, empathy + "\n" + feelings + "이렇게 먼저 이야기를 꺼내 주셔서 고마워요."),
                ask(ctx, cs, next == null ? "request" : next, lead));
    }

    static String parseUrgency(String text, String value, ConsultInterpretation in) {
        if ("URGENT".equals(value) || "SOON".equals(value) || "NORMAL".equals(value)) {
            return value;
        }
        if (in != null && in.urgency() != null && in.urgency().matches("URGENT|SOON|NORMAL")) {
            return in.urgency();
        }
        if (Pattern.compile("급하지 않|괜찮|천천히|여유").matcher(text).find()) {
            return "NORMAL";
        }
        UrgencyLevel l = ConsultText.detectUrgency(text);
        return l == null ? "SOON" : l.name();
    }

    /** 학부모의 답 하나에 상담사의 다음 말(들) */
    public List<ConsultMessageResponse> next(String text, String value, ConsultSession cs, ConsultContext ctx) {
        boolean typed = (value == null || value.isBlank()) && !QUICK_LABELS.contains(text);
        if (value != null && value.isBlank()) {
            value = null;
        }
        // 직접 쓴 말만 AI 해석·감정 판단에 쓴다 (빠른 답은 규칙으로 바로 처리)
        Optional<ConsultInterpretation> in = Optional.empty();
        String feel = null;
        if (typed) {
            cs.secret.parentSaid.add(text);
            in = needsInterpretation(cs) ? ctx.interpret(requestOf(text, cs, ctx)) : Optional.empty();
            boolean agitated = "AGITATED".equals(ConsultText.assessEmotion(List.of(text)).level())
                    || in.map(x -> "AGITATED".equals(x.emotion())).orElse(false);
            if (!cs.emotionAcked && agitated) {
                cs.emotionAcked = true;
                feel = AGITATED_ACK;
            }
        }
        List<ConsultMessageResponse> out = new ArrayList<>(step(text, value, typed, in.orElse(null), cs, ctx));
        if (feel != null) {
            out.add(0, ai(ctx, feel));
        }
        return out;
    }

    private static boolean needsInterpretation(ConsultSession cs) {
        return cs.stage != null;
    }

    private ConsultInterpretation.Request requestOf(String text, ConsultSession cs, ConsultContext ctx) {
        String pending = cs.stage == ConsultStage.RECIPIENT ? "teacher" : cs.secret.pending;
        Question q = pending == null ? null : QUESTIONS.get(pending);
        String question = q != null ? q.content()
                : "method".equals(pending) ? "선생님이 답을 드릴 때 어떤 방법이 편하세요?"
                : "time".equals(pending) ? "연락받기 편한 시간대가 있으실까요?"
                : "teacher".equals(pending) ? "어느 선생님께 전하면 좋을까요?"
                : "덧붙이거나 고칠 내용이 있으면 말씀해 주세요.";
        ConsultSession.Slots s = cs.slots();
        String context = "일시: " + nz(s.when) + " / 장소: " + nz(s.place) + " / 아이 상태: " + nz(s.impact) + " / 바라는 점: " + nz(s.request);
        List<ConsultContext.Teacher> teachers = cs.stage == ConsultStage.RECIPIENT ? ctx.teachers() : List.of();
        String recommended = cs.stage == ConsultStage.RECIPIENT ? recipientReasoning(cs, ctx).pick() : null;
        return new ConsultInterpretation.Request(pending, question, text, context, teachers, recommended);
    }

    private static String nz(String s) {
        return s == null ? "(모름)" : s;
    }

    private List<ConsultMessageResponse> step(String text, String value, boolean typed, ConsultInterpretation in,
                                              ConsultSession cs, ConsultContext ctx) {
        ConsultSession.Slots s = cs.slots();
        ConsultStage stage = cs.stage == null ? ConsultStage.READY : cs.stage;
        switch (stage) {
            case FACTS -> {
                String key = cs.secret.pending;
                String ack = null;
                if ("urgency".equals(key)) {
                    String level = parseUrgency(text, value, in);
                    s.urgency = level;
                    String reason = ConsultText.urgencyReason(String.join(" ", cs.secret.parentSaid));
                    s.urgencyReason = "URGENT".equals(level) ? (reason == null ? "보호자가 빠른 확인 요청" : reason) : null;
                    // 시급도 답은 다른 칸을 채우지 않는다 (프론트 목과 같음). 말 자체는 카드 재료(parentSaid)로 남는다
                    ack = URGENCY_ACK.get(level);
                } else if (key != null && QUESTIONS.containsKey(key)) {
                    if ("UNKNOWN".equals(value)) {
                        s.set(key, "detail".equals(key) ? "given" : "확인 필요");
                        ack = "괜찮아요. 선생님이 확인하실 수 있게 그렇게 적어 둘게요.";
                    } else if ("detail".equals(key)) {
                        s.detail = "given";
                        cs.secret.cardTexts.add(text);
                        apply(in, cs, key, text);
                        extract(text, cs);
                        ack = in != null && present(in.ack()) ? in.ack()
                                : ConsultText.CONCERN.matcher(text).find() || ConsultText.hasEmotion(text)
                                        ? "이야기를 들으니 아이도, 보호자님도 많이 놀라고 속상하셨을 것 같아요. 자세히 들려주셔서 고마워요."
                                        : "자세히 들려주셔서 고마워요. 그때 상황이 조금 더 그려져요.";
                    } else {
                        // 빠른 답은 그대로 칸에 넣고, 자유 입력은 AI 해석·규칙으로 다른 칸도 함께 채운다
                        boolean quick = QUESTIONS.get(key).quick().stream().anyMatch(q -> q.label().equals(text));
                        if (quick) {
                            s.set(key, "request".equals(key) ? REQUEST_LABEL.getOrDefault(text, text) : text);
                        } else {
                            apply(in, cs, key, text);
                            extract(text, cs);
                        }
                        if ("when".equals(key) && s.when == null) {
                            s.when = clip(text, 30);
                        }
                        if ("place".equals(key) && s.place == null) {
                            s.place = clip(text, 30);
                        }
                        if ("impact".equals(key) && s.impact == null) {
                            s.impact = ConsultText.soften(text);
                        }
                        if ("request".equals(key) && s.request == null) {
                            s.request = ConsultText.soften(text);
                        }
                        ack = switch (key) {
                            case "when" -> s.when + " 있었던 일이군요.";
                            case "place" -> Pattern.compile("수업|시간|조회|종례|자율학습|야자").matcher(s.place).find()
                                    ? s.place + " 때였군요. 말씀해 주셔서 고마워요."
                                    : s.place + "에서 있었던 일이군요. 말씀해 주셔서 고마워요.";
                            case "impact" -> !quick && in != null && present(in.ack()) ? in.ack()
                                    : text.contains("괜찮")
                                            ? "겉으로라도 괜찮아 보인다니 다행이에요. 그래도 보호자님 마음 한편은 계속 쓰이시죠."
                                            : "아이가 그렇게 지내고 있다니, 곁에서 지켜보시는 보호자님 마음도 많이 무거우시겠어요.";
                            default -> !quick && in != null && present(in.ack()) ? in.ack() : "네, 바라시는 점을 분명히 담아 둘게요.";
                        };
                    }
                }
                String next = cs.nextEmpty();
                if (next != null) {
                    return List.of(ask(ctx, cs, next, ack));
                }
                List<ConsultMessageResponse> out = new ArrayList<>(explainStage(ctx, cs));
                if (ack != null) {
                    out.add(0, ai(ctx, ack));
                }
                return out;
            }
            case EXPLAIN -> {
                if ("TO_TEACHER".equals(value)) {
                    return recipientStage(ctx, cs);
                }
                if ("MORE".equals(value)) {
                    cs.secret.pending = "detail";
                    return List.of(ai(ctx, "물론이에요. 고치거나 더하고 싶은 내용을 편하게 적어 주세요. 바로 정리에 반영할게요."));
                }
                // 덧붙인 말 → 정리에 반영하고 다시 묻는다 (새로 말한 일시·장소는 기존 값과 합치거나 바꾼다)
                cs.secret.cardTexts.add(text);
                String beforeWhen = s.when;
                String beforePlace = s.place;
                String beforeRequest = s.request;
                s.when = null;
                s.place = null;
                s.request = null;
                apply(in, cs, null, text);
                extract(text, cs);
                s.when = merge(beforeWhen, s.when, Pattern.compile("교시"));
                s.place = s.place != null ? s.place : beforePlace;
                s.request = beforeRequest != null && s.request != null && !beforeRequest.contains(s.request)
                        ? beforeRequest + " / " + s.request
                        : s.request != null ? s.request : beforeRequest;
                AiExplanationResponse explanation = ctx.explain(cs.secret.cardTexts, cs.category);
                return List.of(
                        ai(ctx, "말씀해 주셔서 고마워요. 정리에 반영했어요.", null, factsOf(cs, ctx, false), explanation, null, null),
                        ai(ctx, "이렇게 정리하면 될까요? 다음으로 어떻게 하고 싶으세요?", null, null, null, null, ACTIONS_AFTER_EXPLAIN));
            }
            case RECIPIENT -> {
                List<ConsultContext.Teacher> teachers = ctx.teachers();
                String recommended = recipientReasoning(cs, ctx).pick();
                ConsultContext.Teacher chosen = teachers.stream().filter(t -> t.id().equals(value)).findFirst()
                        .or(() -> teachers.stream().filter(t -> text.contains(t.name())).findFirst())
                        .or(() -> text.contains("추천") ? teachers.stream().filter(t -> t.id().equals(recommended)).findFirst() : Optional.empty())
                        .or(() -> in == null || in.teacherId() == null ? Optional.empty()
                                : teachers.stream().filter(t -> t.id().equals(in.teacherId())).findFirst())
                        .or(() -> text.contains("담임") ? teachers.stream().filter(ConsultContext.Teacher::homeroom).findFirst()
                                : teachers.stream().filter(t -> t.subjects().stream().anyMatch(sub -> !sub.isBlank() && text.contains(sub))).findFirst())
                        .orElse(null);
                if (chosen == null) {
                    Reasoning r = recipientReasoning(cs, ctx);
                    return List.of(ai(ctx, "어느 선생님께 전하면 좋을지, 아래에서 한 분을 골라 주시겠어요?", null, null, null, null,
                            recipientQuick(r.pick(), ctx)));
                }
                cs.proposedTeacherId = chosen.id();
                return contactStage(ctx, cs, "좋아요, " + chosen.name() + " 선생님께 전해 드릴게요.");
            }
            case CONTACT -> {
                if ("method".equals(cs.secret.pending)) {
                    if (value != null && value.matches("MESSAGE|CALL|VISIT")) {
                        s.methods = new ArrayList<>(List.of(value));
                    } else {
                        List<String> fromAi = in == null || in.methods() == null ? List.of()
                                : in.methods().stream().filter(x -> x != null && x.matches("MESSAGE|CALL|VISIT")).distinct().toList();
                        List<String> parsed = ConsultText.parseMethods(text);
                        s.methods = new ArrayList<>(!fromAi.isEmpty() ? fromAi : parsed != null ? parsed : List.of("MESSAGE"));
                    }
                    TimeRange tw = in != null && timeOf(in) != null ? timeOf(in) : ConsultText.parseTimeWindow(text);
                    if (tw != null) {
                        s.timeWindow = tw;
                        s.timeAnswered = true;
                    }
                    String label = s.methods.stream().map(m -> METHOD_LABEL.getOrDefault(m, m)).collect(Collectors.joining("·"));
                    String lead = label + ConsultText.euro(label) + " 연락받으시는 걸로 적어 둘게요.";
                    return s.timeAnswered ? readyStage(ctx, cs, lead) : List.of(askTime(ctx, cs, lead));
                }
                if ("ANY".equals(value) || Pattern.compile("상관없|언제든").matcher(text).find()
                        || (in != null && Boolean.TRUE.equals(in.timeAny()))) {
                    s.timeWindow = null;
                } else if (value != null && value.matches("\\d{2}:\\d{2}-\\d{2}:\\d{2}")) {
                    String[] p = value.split("-");
                    s.timeWindow = new TimeRange(p[0], p[1]);
                } else {
                    TimeRange tw = in != null ? timeOf(in) : null;
                    s.timeWindow = tw != null ? tw : ConsultText.parseTimeWindow(text);
                }
                s.timeAnswered = true;
                return readyStage(ctx, cs, s.timeWindow != null ? s.timeWindow.from() + "~" + s.timeWindow.to() + " 사이로 전해 둘게요."
                        : "시간은 선생님 편한 때로 전해 둘게요.");
            }
            default -> {
                cs.secret.cardTexts.add(text);
                apply(in, cs, null, text);
                extract(text, cs);
                return List.of(ai(ctx, "그 내용도 카드에 함께 담을게요. 더 하실 말씀이 없으면 요약 카드를 만들어 볼게요.", null, null, null, null,
                        List.of(new QuickReply("요약 카드 만들기", "MAKE_CARD"))));
            }
        }
    }

    /** 감정 판단용: 학부모가 직접 쓴 말 (빠른 답 제외) */
    public static boolean isQuickLabel(String text) {
        return QUICK_LABELS.contains(text);
    }

    static List<String> distinct(List<String> in) {
        return new ArrayList<>(new LinkedHashSet<>(in));
    }

    static List<String> words(String s) {
        return Arrays.asList(s.split("\\s+"));
    }
}
