package com.axedu.service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.axedu.common.exception.ApiException;
import com.axedu.common.exception.ErrorCode;
import com.axedu.common.security.AuthUser;
import com.axedu.common.util.TimeUtil;
import com.axedu.dto.chat.TimeRange;
import com.axedu.dto.complaint.EditCardRequest;
import com.axedu.dto.complaint.RevisionRequest;
import com.axedu.dto.complaint.SummaryCardResponse;
import com.axedu.dto.realtime.RealtimeEvents;
import com.axedu.entity.Complaint;
import com.axedu.entity.ComplaintAiExplanation;
import com.axedu.entity.ComplaintExplanationCitation;
import com.axedu.entity.SummaryCard;
import com.axedu.entity.SummaryCardRegulation;
import com.axedu.entity.User;
import com.axedu.entity.enums.AiStatus;
import com.axedu.entity.enums.CardSource;
import com.axedu.entity.enums.ComplaintStatus;
import com.axedu.entity.enums.ComplaintTag;
import com.axedu.entity.enums.ConsultStage;
import com.axedu.entity.enums.ContactMethod;
import com.axedu.entity.enums.UrgencyLevel;
import com.axedu.entity.value.CardSuggestion;
import com.axedu.entity.value.ParentEmotion;
import com.axedu.entity.value.SituationItem;
import com.axedu.repository.ComplaintAiExplanationRepository;
import com.axedu.repository.ComplaintExplanationCitationRepository;
import com.axedu.repository.SummaryCardRegulationRepository;
import com.axedu.repository.SummaryCardRepository;
import com.axedu.service.ai.AiClient;
import com.axedu.service.ai.AiException;
import com.axedu.service.ai.AiModels.CardDraft;
import com.axedu.service.consult.ConsultCardWriter;
import com.axedu.service.consult.ConsultService;
import com.axedu.service.consult.ConsultSession;
import com.axedu.websocket.RealtimePublisher;

import lombok.RequiredArgsConstructor;

/**
 * 요약 카드 생성·말로 수정·직접 수정 (계약서 4.3~4.5). 응답 data 는 카드 자체(SummaryCardResult).
 * 카드는 버전마다 새 행으로 쌓고, 새 카드가 생길 때마다 학부모에게 complaint.card_updated.
 */
@Service
@RequiredArgsConstructor
public class SummaryCardService {

    private final ComplaintService complaintService;
    private final ComplaintAssembler assembler;
    private final SummaryCardRepository cardRepository;
    private final SummaryCardRegulationRepository cardRegulationRepository;
    private final ComplaintAiExplanationRepository explanationRepository;
    private final ComplaintExplanationCitationRepository citationRepository;
    private final RegulationService regulationService;
    private final UserService userService;
    private final AiClient aiClient;
    private final ConsultService consultService;

    private static final Pattern CALM = Pattern.compile("급하지 않|급한 건 아니|천천히|여유");
    private static final Pattern URGENT = Pattern.compile("긴급|급해|급하|빨리");
    private final RealtimePublisher publisher;

    /**
     * POST /complaints/{id}/summary-card — 상담이 READY 일 때 상담 전체로 카드 (AI 2단계).
     * EXPLAINED → CARD_DRAFTING, version 1, consultStage = null. READY 가 아니면 409 + details.stage
     */
    @Transactional
    public SummaryCardResponse create(AuthUser viewer, String id) {
        complaintService.requireRole(viewer, com.axedu.entity.enums.UserRole.PARENT);
        Complaint c = complaintService.load(viewer, id);
        complaintService.requireStatus(c, EnumSet.of(ComplaintStatus.EXPLAINED), "요약 카드를 생성");
        ConsultSession session = consultService.session(c).orElse(null);
        if (session == null || session.stage != ConsultStage.READY) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("stage", session == null || session.stage == null ? null : session.stage.name());
            throw new ApiException(ErrorCode.INVALID_COMPLAINT_STATUS, "상담을 조금 더 나눈 뒤에 요약 카드를 만들 수 있어요.", details);
        }
        // 상담에서 안내한 규정·법령 (최신 설명의 인용) → 카드 근거 자료
        List<String> citations = explanationRepository.findFirstByComplaintIdAndStatusOrderByAttemptDesc(c.getId(), AiStatus.SUCCEEDED)
                .map(e -> citationRepository.findByExplanationIdOrderBySortOrderAsc(e.getId()).stream()
                        .map(ComplaintExplanationCitation::getRegulationChunkId).toList())
                .orElse(List.of());
        String regulationSummary = regulationService.toResponses(citations).stream()
                .map(r -> "- " + r.title() + ": " + r.excerpt()).collect(Collectors.joining("\n"));

        ConsultCardWriter.Card made = consultService.card(c, session, regulationSummary);
        CardDraft draft = new CardDraft(made.tags(), made.situation(), made.concerns(), made.requests(), made.methods(),
                made.preferredFrom(), made.preferredTo(), citations);
        SummaryCard card = saveVersion(c, null, draft, CardSource.AI_GENERATED, null, viewer,
                made.urgency(), made.urgencyReason(), made.parentEmotion());
        consultService.finish(c, session);
        complaintService.transition(c, ComplaintStatus.CARD_DRAFTING, viewer);
        return assembler.card(card, true, false);
    }

    /** POST /complaints/{id}/summary-card/revisions — '말로 수정 요청' (CARD_DRAFTING 유지) */
    @Transactional
    public SummaryCardResponse revise(AuthUser viewer, String id, RevisionRequest req) {
        complaintService.requireRole(viewer, com.axedu.entity.enums.UserRole.PARENT);
        Complaint c = complaintService.load(viewer, id);
        complaintService.requireStatus(c, EnumSet.of(ComplaintStatus.CARD_DRAFTING), "카드를 수정");
        String instruction = req == null || req.instruction() == null ? "" : req.instruction().trim();
        if (instruction.isEmpty()) {
            throw ApiException.validation("수정할 내용을 적어 주세요.", "instruction", "required");
        }
        if (instruction.length() > 1000) {
            throw ApiException.validation("1,000자까지 쓸 수 있어요.", "instruction", "max 1000");
        }
        SummaryCard base = complaintService.latestCardOrConflict(c, req.baseVersion());

        CardDraft revised;
        try {
            revised = aiClient.revise(toDraft(base), instruction);
        } catch (AiException e) {
            revised = null;
        }
        // 대응 시급도는 말로만 바꿀 수 있다: '긴급으로 바꿔 주세요' → URGENT, '급하지 않아요' → NORMAL
        UrgencyLevel urgency = base.getUrgencyLevel();
        String urgencyReason = base.getUrgencyReason();
        boolean calm = CALM.matcher(instruction).find();
        boolean urgencyChanged = false;
        if (!calm && URGENT.matcher(instruction).find()) {
            urgencyChanged = urgency != UrgencyLevel.URGENT;
            urgency = UrgencyLevel.URGENT;
            urgencyReason = urgencyReason == null ? "보호자가 빠른 확인 요청" : urgencyReason;
        } else if (calm) {
            urgencyChanged = urgency != UrgencyLevel.NORMAL;
            urgency = UrgencyLevel.NORMAL;
            urgencyReason = null;
        }
        if (revised == null && (urgencyChanged || calm || URGENT.matcher(instruction).find())) {
            revised = toDraft(base);
        }
        if (revised == null) {
            throw new ApiException(ErrorCode.AI_PROCESSING_FAILED,
                    "어떻게 고칠지 알아듣지 못했어요. 예: 요청 사항에 '보관 시간 단축 검토'도 넣어 주세요.");
        }
        SummaryCard card = saveVersion(c, base, revised, CardSource.AI_REVISION, instruction, viewer,
                urgency, urgencyReason, base.getParentEmotion());
        return assembler.card(card, true, false);
    }

    /** PATCH /complaints/{id}/summary-card — 직접 수정. 보낸 키만 바꾸고, 검증 실패는 details.fields 로 한 번에 */
    @Transactional
    public SummaryCardResponse edit(AuthUser viewer, String id, EditCardRequest req) {
        complaintService.requireRole(viewer, com.axedu.entity.enums.UserRole.PARENT);
        Complaint c = complaintService.load(viewer, id);
        complaintService.requireStatus(c, EnumSet.of(ComplaintStatus.CARD_DRAFTING), "카드를 수정");
        SummaryCard base = complaintService.latestCardOrConflict(c, req == null ? null : req.baseVersion());
        CardDraft current = toDraft(base);

        List<String> tags = req.basicInfo() != null && req.basicInfo().tags() != null ? req.basicInfo().tags() : current.tags();
        List<SituationItem> situation = req.situation() != null ? req.situation() : current.situation();
        List<String> concerns = req.concerns() != null ? req.concerns() : current.concerns();
        List<String> requests = req.requests() != null ? req.requests() : current.requests();
        List<String> methods = current.preferredMethods();
        LocalTime from = current.preferredFrom();
        LocalTime to = current.preferredTo();

        Map<String, String> errors = new LinkedHashMap<>();
        if (req.preferredContact() != null) {
            methods = req.preferredContact().methods() == null ? List.of() : req.preferredContact().methods();
            TimeRange tw = req.preferredContact().timeWindow();
            from = tw == null ? null : TimeUtil.parseHhmm(tw.from());
            to = tw == null ? null : TimeUtil.parseHhmm(tw.to());
            if (methods.isEmpty()) {
                errors.put("preferredContact", "연락 방법을 하나 이상 골라 주세요.");
            } else if (!methods.stream().allMatch(m -> ComplaintService.isEnum(ContactMethod.class, m))) {
                errors.put("preferredContact", "연락 방법 값이 올바르지 않습니다.");
            }
            if (tw != null && (from == null || to == null || !from.isBefore(to))) {
                errors.put("preferredContact", "연락받을 시간의 시작이 끝보다 빨라야 해요.");
            }
        }
        if (situation.isEmpty() || situation.stream().anyMatch(s -> s == null || s.what() == null || s.what().isBlank())) {
            errors.put("situation", "상황을 한 가지 이상 적어 주세요.");
        }
        if (requests.isEmpty() || requests.stream().anyMatch(r -> r == null || r.isBlank())) {
            errors.put("requests", "요청 사항을 한 가지 이상 적어 주세요.");
        }
        if (!tags.stream().allMatch(t -> ComplaintService.isEnum(ComplaintTag.class, t))) {
            errors.put("tags", "민원 유형 값이 올바르지 않습니다.");
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }

        // 서버가 배열 순서대로 order 를 다시 매긴다. attribution·regulations 는 학부모가 고칠 수 없음
        List<SituationItem> ordered = new ArrayList<>();
        for (int i = 0; i < situation.size(); i++) {
            SituationItem s = situation.get(i);
            ordered.add(new SituationItem(i + 1, nz(s.when()), nz(s.where()), s.what().trim()));
        }
        CardDraft next = new CardDraft(tags, ordered, concerns.stream().filter(x -> x != null && !x.isBlank()).toList(),
                requests.stream().map(String::trim).toList(), methods, from, to, current.regulationChunkIds());
        // urgency·parentEmotion 은 직접 고칠 수 없고 이전 버전 값을 이어받는다
        SummaryCard card = saveVersion(c, base, next, CardSource.PARENT_EDIT, null, viewer,
                base.getUrgencyLevel(), base.getUrgencyReason(), base.getParentEmotion());
        return assembler.card(card, true, false);
    }

    // ---------------- 내부 ----------------

    private SummaryCard saveVersion(Complaint c, SummaryCard base, CardDraft draft, CardSource source, String instruction, AuthUser viewer,
                                    UrgencyLevel urgency, String urgencyReason, ParentEmotion emotion) {
        SummaryCard card = new SummaryCard();
        card.setComplaintId(c.getId());
        card.setVersion(base == null ? 1 : base.getVersion() + 1);
        card.setSource(source);
        card.setRevisionInstruction(instruction);
        if (base != null) {
            card.setGrade(base.getGrade());
            card.setClassNo(base.getClassNo());
            card.setReceivedAt(base.getReceivedAt());
            card.setAttribution(base.getAttribution());
        } else {
            User student = userService.get(c.getStudentId());
            card.setGrade(gradeOf(student));
            card.setClassNo(classOf(student));
            card.setReceivedAt(c.getCreatedAt());
            card.setAttribution(SummaryCard.DEFAULT_ATTRIBUTION);
        }
        card.setTags(new ArrayList<>(draft.tags() == null || draft.tags().isEmpty() ? List.of(ComplaintTag.ETC.name()) : draft.tags()));
        card.setSituation(new ArrayList<>(draft.situation()));
        card.setConcerns(new ArrayList<>(draft.concerns()));
        card.setRequests(new ArrayList<>(draft.requests()));
        card.setPreferredMethods(new ArrayList<>(draft.preferredMethods()));
        card.setPreferredFrom(draft.preferredFrom());
        card.setPreferredTo(draft.preferredTo());
        List<CardSuggestion> suggestions = aiClient.suggest(draft);
        card.setSuggestions(suggestions == null || suggestions.isEmpty() ? null : new ArrayList<>(suggestions));
        card.setUrgencyLevel(urgency == null ? UrgencyLevel.NORMAL : urgency);
        card.setUrgencyReason(urgency == UrgencyLevel.URGENT ? urgencyReason : null);
        card.setParentEmotion(emotion);
        card.setCreatedBy(viewer.id());
        card.setConfirmedAt(null);
        card = cardRepository.save(card);

        short order = 0;
        for (String chunkId : regulationService.existingChunkIds(draft.regulationChunkIds())) {
            cardRegulationRepository.save(new SummaryCardRegulation(card.getId(), chunkId, order++));
        }
        publisher.publish(c.getParentId(), RealtimeEvents.COMPLAINT_CARD_UPDATED, new RealtimeEvents.CardUpdated(c.getId(), card.getVersion()));
        return card;
    }

    private CardDraft toDraft(SummaryCard card) {
        List<String> chunkIds = cardRegulationRepository.findByCardIdOrderBySortOrderAsc(card.getId()).stream()
                .map(SummaryCardRegulation::getRegulationChunkId).toList();
        return new CardDraft(List.copyOf(card.getTags()), List.copyOf(card.getSituation()), List.copyOf(card.getConcerns()),
                List.copyOf(card.getRequests()), List.copyOf(card.getPreferredMethods()), card.getPreferredFrom(), card.getPreferredTo(),
                chunkIds);
    }

    /** 학생 학년 (없으면 학번 첫 자리) */
    static short gradeOf(User s) {
        if (s.getGrade() != null) {
            return s.getGrade();
        }
        String n = s.getStudentNumber();
        return n != null && n.length() == 5 ? Short.parseShort(n.substring(0, 1)) : 0;
    }

    static short classOf(User s) {
        if (s.getClassNo() != null) {
            return s.getClassNo();
        }
        String n = s.getStudentNumber();
        return n != null && n.length() == 5 ? Short.parseShort(n.substring(1, 3)) : 0;
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}
