package com.axedu.service.consult;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.axedu.common.exception.ApiException;
import com.axedu.common.exception.ErrorCode;
import com.axedu.common.security.TextCipher;
import com.axedu.common.util.IdGenerator;
import com.axedu.common.util.TimeUtil;
import com.axedu.dto.complaint.AiExplanationResponse;
import com.axedu.dto.complaint.ConsultMessageResponse;
import com.axedu.entity.Complaint;
import com.axedu.entity.User;
import com.axedu.entity.enums.ComplaintTag;
import com.axedu.entity.enums.ConsultStage;
import com.axedu.service.ComplaintMasker;
import com.axedu.service.ComplaintRecipientService;
import com.axedu.service.UserService;
import com.axedu.service.ai.AiException;

import lombok.RequiredArgsConstructor;

/**
 * 똑똑 AI 상담사 (POST /complaints 첫 응답, POST /complaints/{id}/consultations, 카드 재료).
 * 진행 규칙은 {@link ConsultEngine}, 저장은 {@link ConsultStore}, AI 호출은 {@link ConsultAiClient}·{@link ConsultExplainer}·{@link ConsultCardWriter}.
 * 호출하는 쪽(ComplaintService·SummaryCardService)의 트랜잭션 안에서 쓴다.
 */
@Service
@RequiredArgsConstructor
public class ConsultService {

    /** 상담 메시지 상한 (넘으면 요약 카드로 정리하도록 안내) */
    public static final int MAX_MESSAGES = 40;
    public static final int MAX_CONTENT = 2000;
    /** 로컬 테스트용: 민원 원문에 이 표시가 있으면 첫 시도를 한 번 실패시킨다 (프론트 목과 같은 동작) */
    static final String FAIL_MARK = "[AI실패]";

    private final ConsultStore store;
    private final ConsultEngine engine;
    private final ConsultExplainer explainer;
    private final ConsultCardWriter cardWriter;
    private final ConsultAiClient ai;
    private final ComplaintRecipientService recipientService;
    private final UserService userService;
    private final ComplaintMasker masker;
    private final TextCipher cipher;

    /** 민원 생성: 상담 상태(사전 분류) + 학부모의 첫 메시지 저장. 첫 AI 응답은 {@link #start} */
    public void open(Complaint c, ComplaintTag category, String text) {
        ConsultSession s = new ConsultSession();
        s.category = category;
        store.save(c.getId(), s);
        store.append(c.getId(), List.of(parentMessage(text, null)));
    }

    /**
     * 상담사의 첫 반응 (공감 + 첫 질문) → FACTS. 이미 시작했으면 아무것도 하지 않는다.
     *
     * @param firstAttempt POST /complaints 에서 부르면 true, explanation/retry 면 false
     * @throws AiException 첫 응답을 만들지 못함 → 422 AI_PROCESSING_FAILED (상담 단계는 null 로 남는다)
     */
    public void start(Complaint c, boolean firstAttempt) {
        ConsultSession s = store.load(c.getId()).orElseGet(ConsultSession::new);
        if (s.stage != null) {
            return;
        }
        String text = cipher.decrypt(c.getOriginalTextEnc());
        if (firstAttempt && text.contains(FAIL_MARK)) {
            throw new AiException("TIMEOUT", "첫 응답 실패 재현");
        }
        List<ConsultMessageResponse> out = engine.start(text, s, context(c, s));
        store.save(c.getId(), s);
        store.append(c.getId(), out);
    }

    /** 상담 한 턴: 학부모의 답 → 상담사의 다음 말 (status 는 EXPLAINED 그대로, consultStage 만 바뀜) */
    public void turn(Complaint c, String content, String value) {
        ConsultSession s = store.load(c.getId()).orElse(null);
        if (s == null || s.stage == null) {
            throw new ApiException(ErrorCode.AI_PROCESSING_FAILED, "첫 답변을 먼저 받아 주세요.", Map.of("complaintId", c.getId()));
        }
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw ApiException.validation("내용을 입력해 주세요.", "content", "required");
        }
        if (text.length() > MAX_CONTENT) {
            throw ApiException.validation("2,000자까지 쓸 수 있어요.", "content", "max 2000");
        }
        if (store.count(c.getId()) >= MAX_MESSAGES) {
            throw ApiException.validation("상담이 길어졌어요. '요약 카드 만들기'로 정리해 주세요.", "content", "max turns");
        }
        String v = value == null || value.isBlank() ? null : value.trim();
        if (v != null && v.length() > 64) {
            throw ApiException.validation("빠른 답 값이 올바르지 않습니다.", "value", "invalid");
        }
        List<ConsultMessageResponse> out = new ArrayList<>();
        out.add(parentMessage(text, v));
        out.addAll(engine.next(text, v, s, context(c, s)));
        store.save(c.getId(), s);
        store.append(c.getId(), out);
    }

    public Optional<ConsultSession> session(Complaint c) {
        return store.load(c.getId());
    }

    /** 상담에서 정한 받는 교사 (없으면 null) */
    public String proposedTeacherId(Complaint c) {
        return store.row(c.getId()).map(r -> r.getProposedTeacherId()).orElse(null);
    }

    /** 요약 카드 재료 (AI 2단계). regulationSummary = 상담에서 안내한 규정 요약 */
    public ConsultCardWriter.Card card(Complaint c, ConsultSession s, String regulationSummary) {
        return cardWriter.write(c, s, masker.mask(c, factsText(s, c)), regulationSummary);
    }

    /** 카드를 만들었으면 상담 단계는 끝 (consultStage = null) */
    public void finish(Complaint c, ConsultSession s) {
        s.stage = null;
        s.secret.pending = null;
        store.save(c.getId(), s);
    }

    // ---------------- 내부 ----------------

    private ConsultMessageResponse parentMessage(String text, String value) {
        return new ConsultMessageResponse(IdGenerator.newId(IdGenerator.CONSULT_MESSAGE), "PARENT", text, null, null, null, null, null,
                value, TimeUtil.now());
    }

    String factsText(ConsultSession s, Complaint c) {
        ConsultSession.Slots x = s.slots();
        List<String> lines = new ArrayList<>();
        lines.add("대응 시급도: " + (x.urgency == null ? "확인 필요" : ConsultEngine.URGENCY_LABEL.get(x.urgency))
                + (x.urgencyReason == null ? "" : " (" + x.urgencyReason + ")"));
        lines.add("일시: " + nz(x.when));
        lines.add("장소·수업: " + nz(x.place));
        lines.add("아이 상태: " + nz(x.impact));
        lines.add("바라는 점: " + nz(x.request));
        if (x.methods != null) {
            lines.add("희망 연락: " + String.join("·", x.methods) + (x.timeWindow == null ? "" : " " + x.timeWindow.from() + "~" + x.timeWindow.to()));
        }
        return String.join("\n", lines);
    }

    private static String nz(String s) {
        return s == null || s.equals("given") ? "확인 필요" : s;
    }

    private ConsultContext context(Complaint c, ConsultSession s) {
        return new Ctx(c, s);
    }

    /** 민원 한 건에 묶인 상담 컨텍스트 */
    private final class Ctx implements ConsultContext {

        private final Complaint complaint;
        private final ConsultSession session;
        private final Map<String, String> names = new HashMap<>();
        private List<Teacher> teachers;

        Ctx(Complaint complaint, ConsultSession session) {
            this.complaint = complaint;
            this.session = session;
        }

        @Override
        public List<Teacher> teachers() {
            if (teachers == null) {
                User student = userService.get(complaint.getStudentId());
                List<ComplaintRecipientService.Candidate> candidates = recipientService.candidates(student);
                Map<String, String> byId = userService.summaries(candidates.stream().map(ComplaintRecipientService.Candidate::teacherId).toList())
                        .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().name()));
                names.putAll(byId);
                teachers = candidates.stream()
                        .map(x -> new Teacher(x.teacherId(), byId.getOrDefault(x.teacherId(), "선생님"), x.homeroom(), x.subjects()))
                        .toList();
            }
            return teachers;
        }

        @Override
        public String fixedTeacherId() {
            return complaint.getTeacherId();
        }

        @Override
        public String teacherName(String id) {
            if (id == null) {
                return "";
            }
            return names.computeIfAbsent(id, k -> userService.get(k).getName());
        }

        @Override
        public AiExplanationResponse explain(List<String> cardTexts, ComplaintTag category) {
            return explainer.explain(complaint, cardTexts, category, masker.mask(complaint, factsText(session, complaint)));
        }

        @Override
        public Optional<ConsultInterpretation> interpret(ConsultInterpretation.Request request) {
            if (!ai.enabled()) {
                return Optional.empty();
            }
            // AI 에는 가명처리한 문장만 보낸다
            return ai.interpret(new ConsultInterpretation.Request(request.pending(), request.question(),
                    masker.mask(complaint, request.text()),
                    request.context() == null ? null : masker.mask(complaint, request.context()),
                    request.teachers(), request.recommendedTeacherId()));
        }

        @Override
        public String newMessageId() {
            return IdGenerator.newId(IdGenerator.CONSULT_MESSAGE);
        }

        @Override
        public OffsetDateTime now() {
            return TimeUtil.now();
        }
    }

    /** 상담 단계 (프론트 consultStage) */
    public static ConsultStage stageOf(ConsultSession s) {
        return s == null ? null : s.stage;
    }
}
