package com.axedu.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.axedu.common.security.AuthUser;
import com.axedu.common.security.TextCipher;
import com.axedu.common.util.TimeUtil;
import com.axedu.dto.chat.TimeRange;
import com.axedu.dto.complaint.AiExplanationResponse;
import com.axedu.dto.complaint.CardUrgency;
import com.axedu.dto.complaint.ConsultMessageResponse;
import com.axedu.dto.complaint.ComplaintResponse;
import com.axedu.dto.complaint.EscalationResponse;
import com.axedu.dto.complaint.SummaryCardResponse;
import com.axedu.dto.complaint.TeacherReplyResponse;
import com.axedu.dto.user.UserSummary;
import com.axedu.entity.Complaint;
import com.axedu.entity.ComplaintConsultState;
import com.axedu.entity.ComplaintAiExplanation;
import com.axedu.entity.ComplaintExplanationCitation;
import com.axedu.entity.Escalation;
import com.axedu.entity.SummaryCard;
import com.axedu.entity.SummaryCardRegulation;
import com.axedu.entity.enums.AiStatus;
import com.axedu.entity.enums.ComplaintStatus;
import com.axedu.entity.enums.ComplaintTag;
import com.axedu.entity.enums.ConsultStage;
import com.axedu.entity.enums.UserRole;
import com.axedu.repository.ComplaintAiExplanationRepository;
import com.axedu.repository.ComplaintExplanationCitationRepository;
import com.axedu.repository.ComplaintReplyRepository;
import com.axedu.repository.EscalationRepository;
import com.axedu.repository.SummaryCardRegulationRepository;
import com.axedu.repository.SummaryCardRepository;
import com.axedu.service.consult.ConsultStore;

import lombok.RequiredArgsConstructor;

/**
 * 민원 응답 조립 (계약서 2.13 역할별 응답 모양).
 * 교사: originalText·aiExplanation 키 생략, summaryCard 는 학부모가 제출한 버전.
 * 학부모·대응팀·관리자: 원문 포함, 최신 카드.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComplaintAssembler {

    private final UserService userService;
    private final TextCipher cipher;
    private final SummaryCardRepository cardRepository;
    private final SummaryCardRegulationRepository cardRegulationRepository;
    private final ComplaintAiExplanationRepository explanationRepository;
    private final ComplaintExplanationCitationRepository citationRepository;
    private final ComplaintReplyRepository replyRepository;
    private final EscalationRepository escalationRepository;
    private final RegulationService regulationService;
    private final ConsultStore consultStore;

    public ComplaintResponse view(Complaint c, AuthUser viewer) {
        return view(c, viewer, true);
    }

    /**
     * @param withTranscript false 면 상담 대화(consultation)를 뺀다 (목록 응답)
     */
    public ComplaintResponse view(Complaint c, AuthUser viewer, boolean withTranscript) {
        boolean teacher = viewer.role() == UserRole.TEACHER;
        // 방 밖에서 시작해 아직 제출 전인 민원은 teacher·roomId 가 null
        Map<String, UserSummary> users = userService.summaries(Arrays.asList(c.getParentId(), c.getTeacherId(), c.getStudentId()));

        SummaryCard card;
        if (teacher) {
            card = c.getSubmittedCardVersion() == null ? null
                    : cardRepository.findByComplaintIdAndVersion(c.getId(), c.getSubmittedCardVersion()).orElse(null);
        } else {
            card = cardRepository.findFirstByComplaintIdOrderByVersionDesc(c.getId()).orElse(null);
        }

        Optional<String> originalText = teacher ? null : Optional.of(cipher.decrypt(c.getOriginalTextEnc()));
        Optional<AiExplanationResponse> aiExplanation = teacher ? null : Optional.ofNullable(explanation(c.getId()));

        // AI 상담 (학부모 본인·대응팀만). 교사에게는 키 자체를 뺀다
        List<ConsultMessageResponse> consultation = null;
        Optional<ConsultStage> consultStage = null;
        Optional<UserSummary> proposedTeacher = null;
        Optional<ComplaintTag> category = null;
        if (!teacher) {
            Optional<ComplaintConsultState> state = consultStore.row(c.getId());
            boolean consulting = c.getStatus() == ComplaintStatus.EXPLAINED || c.getStatus() == ComplaintStatus.CLOSED_BY_EXPLANATION;
            consultStage = Optional.ofNullable(consulting ? state.map(ComplaintConsultState::getStage).orElse(null) : null);
            String proposed = state.map(ComplaintConsultState::getProposedTeacherId).orElse(null);
            proposedTeacher = Optional.ofNullable(proposed == null ? null : userService.summary(userService.get(proposed)));
            category = Optional.ofNullable(state.map(ComplaintConsultState::getCategory).orElse(null));
            consultation = withTranscript ? consultStore.messages(c.getId()) : null;
        }

        return new ComplaintResponse(
                c.getId(),
                c.getStatus(),
                users.get(c.getParentId()),
                c.getTeacherId() == null ? null : users.get(c.getTeacherId()),
                users.get(c.getStudentId()),
                c.getRoomId(),
                originalText,
                aiExplanation,
                consultation,
                consultStage,
                proposedTeacher,
                category,
                card == null ? null : card(card, false, viewer.role() != UserRole.PARENT),
                replyRepository.findById(c.getId())
                        .map(r -> new TeacherReplyResponse(r.getContent(), r.getContactMethod(), r.getScheduledAt(), r.getSentAt()))
                        .orElse(null),
                escalationRepository.findByComplaintId(c.getId()).map(this::escalation).orElse(null),
                c.getCreatedAt(),
                c.getSubmittedAt(),
                c.getClosedAt());
    }

    /** 최신 성공 AI 설명. 없으면 null */
    public AiExplanationResponse explanation(String complaintId) {
        return explanationRepository.findFirstByComplaintIdAndStatusOrderByAttemptDesc(complaintId, AiStatus.SUCCEEDED)
                .map(this::explanation)
                .orElse(null);
    }

    public AiExplanationResponse explanation(ComplaintAiExplanation e) {
        List<String> chunkIds = citationRepository.findByExplanationIdOrderBySortOrderAsc(e.getId()).stream()
                .map(ComplaintExplanationCitation::getRegulationChunkId).toList();
        return new AiExplanationResponse(e.getEmpathy(), e.getReason(), e.getAlternative(),
                regulationService.toResponses(chunkIds), e.getRecommendation());
    }

    /**
     * SummaryCard / SummaryCardResult.
     *
     * @param withSuggestions 카드 API 응답에서만 true
     * @param withEmotion     학부모 감정 상태 포함 여부 — 교사·민원대응팀·관리자만 true (학부모 응답에서는 키를 뺀다)
     */
    public SummaryCardResponse card(SummaryCard card, boolean withSuggestions, boolean withEmotion) {
        List<String> chunkIds = cardRegulationRepository.findByCardIdOrderBySortOrderAsc(card.getId()).stream()
                .map(SummaryCardRegulation::getRegulationChunkId).toList();
        TimeRange window = card.getPreferredFrom() == null || card.getPreferredTo() == null ? null
                : new TimeRange(TimeUtil.hhmm(card.getPreferredFrom()), TimeUtil.hhmm(card.getPreferredTo()));
        return new SummaryCardResponse(
                card.getVersion(),
                new SummaryCardResponse.BasicInfo(card.getGrade(), card.getClassNo(), card.getReceivedAt(), List.copyOf(card.getTags())),
                card.getAttribution(),
                List.copyOf(card.getSituation()),
                List.copyOf(card.getConcerns()),
                List.copyOf(card.getRequests()),
                new SummaryCardResponse.PreferredContact(List.copyOf(card.getPreferredMethods()), window),
                regulationService.toResponses(chunkIds),
                new CardUrgency(card.getUrgencyLevel(), card.getUrgencyReason()),
                withEmotion ? card.getParentEmotion() : null,
                card.getConfirmedAt(),
                withSuggestions && card.getSuggestions() != null ? List.copyOf(card.getSuggestions()) : null);
    }

    public EscalationResponse escalation(Escalation e) {
        UserSummary assignee = e.getAssigneeId() == null ? null : userService.summary(userService.get(e.getAssigneeId()));
        return new EscalationResponse(e.getId(), List.copyOf(e.getReasonTypes()), e.getDescription(), e.getStage(), e.getStatus(),
                assignee, e.getRequestedAt());
    }
}
