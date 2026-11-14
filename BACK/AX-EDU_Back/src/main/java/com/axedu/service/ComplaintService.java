package com.axedu.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.axedu.common.exception.ApiException;
import com.axedu.common.exception.ErrorCode;
import com.axedu.common.security.AuthUser;
import com.axedu.common.security.TextCipher;
import com.axedu.common.util.IdGenerator;
import com.axedu.common.util.TimeUtil;
import com.axedu.dto.complaint.ComplaintResponse;
import com.axedu.entity.enums.ComplaintTag;
import com.axedu.service.consult.ConsultService;
import com.axedu.dto.complaint.ConsultRequest;
import com.axedu.dto.user.UserSummary;
import com.axedu.dto.complaint.ComplaintRecipientResponse;
import com.axedu.dto.complaint.CreateComplaintRequest;
import com.axedu.dto.complaint.EscalationRequest;
import com.axedu.dto.complaint.EscalationResponse;
import com.axedu.dto.complaint.ReplyDraftRequest;
import com.axedu.dto.complaint.ReplyDraftResponse;
import com.axedu.dto.complaint.ReplyRequest;
import com.axedu.dto.complaint.ReplyResultResponse;
import com.axedu.dto.complaint.SubmitRequest;
import com.axedu.dto.realtime.RealtimeEvents;
import com.axedu.entity.ChatRoom;
import com.axedu.entity.Complaint;
import com.axedu.entity.ComplaintAccessLog;
import com.axedu.entity.ComplaintAiExplanation;
import com.axedu.entity.ComplaintAttachment;
import com.axedu.entity.ComplaintExplanationCitation;
import com.axedu.entity.ComplaintReply;
import com.axedu.entity.ComplaintStatusHistory;
import com.axedu.entity.Escalation;
import com.axedu.entity.EscalationLog;
import com.axedu.entity.ResponseTeamMember;
import com.axedu.entity.StoredFile;
import com.axedu.entity.SummaryCard;
import com.axedu.entity.TeacherReplyDraft;
import com.axedu.entity.User;
import com.axedu.entity.enums.AccessType;
import com.axedu.entity.enums.AiStatus;
import com.axedu.entity.enums.ComplaintStatus;
import com.axedu.entity.enums.ContactMethod;
import com.axedu.entity.enums.EscalationAction;
import com.axedu.entity.enums.EscalationReason;
import com.axedu.entity.enums.EscalationStatus;
import com.axedu.entity.enums.FilePurpose;
import com.axedu.entity.enums.MessageType;
import com.axedu.entity.enums.RoomType;
import com.axedu.entity.enums.UserRole;
import com.axedu.repository.ChatRoomRepository;
import com.axedu.repository.ComplaintAccessLogRepository;
import com.axedu.repository.ComplaintAiExplanationRepository;
import com.axedu.repository.ComplaintAttachmentRepository;
import com.axedu.repository.ComplaintExplanationCitationRepository;
import com.axedu.repository.ComplaintReplyRepository;
import com.axedu.repository.ComplaintRepository;
import com.axedu.repository.ComplaintStatusHistoryRepository;
import com.axedu.repository.EscalationLogRepository;
import com.axedu.repository.EscalationRepository;
import com.axedu.repository.ParentStudentRepository;
import com.axedu.repository.ResponseTeamMemberRepository;
import com.axedu.repository.SummaryCardRepository;
import com.axedu.repository.TeacherReplyDraftRepository;
import com.axedu.service.ai.AiClient;
import com.axedu.service.ai.AiException;
import com.axedu.service.ai.AiModels;
import com.axedu.websocket.RealtimePublisher;

import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;

/**
 * 민원 생명주기 (계약서 4장). 역할이 맞지 않으면 403, 상태가 맞지 않으면 409 INVALID_COMPLAINT_STATUS(details.status),
 * 볼 수 없는 민원은 404. 상태가 바뀔 때마다 당사자에게 complaint.status_changed.
 */
@Service
@RequiredArgsConstructor
public class ComplaintService {

    public static final int MAX_TEXT = 2000;
    static final String EP_CREATE = "POST /complaints";
    static final String EP_SUBMIT = "POST /complaints/{id}/submit";
    private static final Set<ComplaintStatus> TEACHER_ACTIVE =
            EnumSet.of(ComplaintStatus.SUBMITTED, ComplaintStatus.TEACHER_REVIEWING, ComplaintStatus.IN_CONVERSATION);

    private final ComplaintRepository complaintRepository;
    private final ComplaintAttachmentRepository complaintAttachmentRepository;
    private final ComplaintAiExplanationRepository explanationRepository;
    private final ComplaintExplanationCitationRepository citationRepository;
    private final ComplaintStatusHistoryRepository historyRepository;
    private final ComplaintReplyRepository replyRepository;
    private final ComplaintAccessLogRepository accessLogRepository;
    private final TeacherReplyDraftRepository draftRepository;
    private final SummaryCardRepository cardRepository;
    private final EscalationRepository escalationRepository;
    private final EscalationLogRepository escalationLogRepository;
    private final ResponseTeamMemberRepository teamRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final ChatRoomRepository roomRepository;
    private final ChatRoomService chatRoomService;
    private final ComplaintRecipientService recipientService;
    private final ConsultService consultService;
    private final ComplaintMasker masker;
    private final RoomContextService roomContextService;
    private final MessageService messageService;
    private final FileService fileService;
    private final UserService userService;
    private final RegulationService regulationService;
    private final IdempotencyService idempotencyService;
    private final ReservationClient reservationClient;
    private final ComplaintAssembler assembler;
    private final AiClient aiClient;
    private final TextCipher cipher;
    private final RealtimePublisher publisher;
    private final TransactionTemplate tx;

    // =====================================================================
    // 학부모: 최초 민원 → AI 설명 → (종료 | 카드 작성) → 제출
    // =====================================================================

    /**
     * POST /complaints. 민원을 먼저 저장(커밋)한 뒤 AI 설명을 받는다.
     * AI 가 실패해도 민원은 남기고 422 AI_PROCESSING_FAILED + details.complaintId (프론트가 retry 호출)
     */
    public ComplaintResponse create(AuthUser viewer, CreateComplaintRequest req, String idempotencyKey) {
        requireRole(viewer, UserRole.PARENT);
        Optional<String> dup = idempotencyService.find(viewer.id(), idempotencyKey, EP_CREATE);
        if (dup.isPresent()) {
            return tx.execute(s -> assembler.view(complaintRepository.findById(dup.get()).orElseThrow(), viewer));
        }
        String complaintId = tx.execute(s -> persistNew(viewer, req, idempotencyKey));
        // 상담사의 첫 반응 (공감 + 첫 질문). 실패해도 원문·첫 메시지는 남기고 422 (consultStage = null → retry)
        startConsult(complaintId, true);
        return tx.execute(s -> assembler.view(complaintRepository.findById(complaintId).orElseThrow(), viewer));
    }

    private void startConsult(String complaintId, boolean firstAttempt) {
        try {
            tx.executeWithoutResult(s -> consultService.start(complaintRepository.findById(complaintId).orElseThrow(), firstAttempt));
        } catch (AiException e) {
            throw aiFailed(complaintId);
        }
    }

    private String persistNew(AuthUser viewer, CreateComplaintRequest req, String idempotencyKey) {
        if (req == null) {
            throw ApiException.validation("내용을 입력해 주세요.", "content", "required");
        }
        // 방에서 시작하면 그 방의 선생님으로 고정, 학부모 첫 화면(민원하기)에서 시작하면 제출할 때 고른다
        RoomContext ctx = null;
        if (req.roomId() != null && !req.roomId().isBlank()) {
            ctx = roomContextService.requireActiveMember(req.roomId(), viewer);
            if (ctx.room().getType() != RoomType.DIRECT || ctx.teacher() == null) {
                throw ApiException.notFound("채팅방을 찾을 수 없습니다.");
            }
        }
        String studentId = resolveStudentId(viewer, req.studentId());
        ComplaintTag category = null;
        if (req.category() != null && !req.category().isBlank()) {
            if (!isEnum(ComplaintTag.class, req.category())) {
                throw ApiException.validation("분류를 다시 골라 주세요.", "category", "invalid");
            }
            category = ComplaintTag.valueOf(req.category());
        }
        String content = req.content() == null ? "" : req.content().trim();
        if (content.isEmpty()) {
            throw ApiException.validation("내용을 입력해 주세요.", "content", "required");
        }
        if (content.length() > MAX_TEXT) {
            throw ApiException.validation("2,000자까지 쓸 수 있어요.", "content", "max 2000");
        }

        Complaint c = new Complaint();
        c.setId(IdGenerator.newId(IdGenerator.COMPLAINT));
        c.setSchoolId(viewer.schoolId());
        c.setRoomId(ctx == null ? null : ctx.room().getId());
        c.setParentId(viewer.id());
        c.setTeacherId(ctx == null ? null : ctx.teacher().getId());
        c.setStudentId(studentId);
        c.setStatus(ComplaintStatus.EXPLAINED);
        c.setOriginalTextEnc(cipher.encrypt(content));
        c.setCreatedAt(TimeUtil.now());
        c = complaintRepository.save(c);

        List<StoredFile> files = fileService.attach(viewer, req.attachmentIds(), FilePurpose.COMPLAINT);
        short order = 0;
        for (StoredFile f : files) {
            complaintAttachmentRepository.save(new ComplaintAttachment(c.getId(), f.getId(), order++));
        }

        consultService.open(c, category, content);
        history(c.getId(), null, ComplaintStatus.EXPLAINED, viewer);
        publisher.publish(c.getParentId(), RealtimeEvents.COMPLAINT_STATUS_CHANGED,
                new RealtimeEvents.StatusChanged(c.getId(), null, ComplaintStatus.EXPLAINED, c.getCreatedAt()));
        idempotencyService.remember(viewer.id(), idempotencyKey, EP_CREATE, 201, c.getId());
        return c.getId();
    }

    /** studentId 가 비어 있고 자녀가 한 명이면 그 자녀로 본다 (토큰에 childStudentIds 가 없던 프론트 호환) */
    private String resolveStudentId(AuthUser viewer, String studentId) {
        if (studentId == null || studentId.isBlank()) {
            List<String> children = userService.childStudentIds(viewer.id());
            if (children.size() == 1) {
                return children.get(0);
            }
            throw ApiException.validation("자녀를 골라 주세요.", "studentId", "required");
        }
        if (!parentStudentRepository.existsByParentIdAndStudentId(viewer.id(), studentId)) {
            throw ApiException.forbidden("자녀로 등록된 학생이 아닙니다.");
        }
        return studentId;
    }

    /** POST /complaints/{id}/explanation/retry — 상담 첫 응답(공감 + 첫 질문) 재시도. EXPLAINED 유지, 이미 시작했으면 그대로 */
    public ComplaintResponse retryExplanation(AuthUser viewer, String id) {
        requireRole(viewer, UserRole.PARENT);
        tx.executeWithoutResult(s -> requireStatus(load(viewer, id), EnumSet.of(ComplaintStatus.EXPLAINED), "AI 답변을 다시 요청"));
        startConsult(id, false);
        return tx.execute(s -> assembler.view(load(viewer, id), viewer));
    }

    /**
     * POST /complaints/{id}/consultations — 상담 한 턴 (학부모 답 → 상담사의 다음 말). status 는 EXPLAINED 그대로.
     * 첫 응답 전이면 422 AI_PROCESSING_FAILED + details.complaintId, 메시지 40개 초과·내용 오류는 400
     */
    @Transactional
    public ComplaintResponse consult(AuthUser viewer, String id, ConsultRequest req) {
        requireRole(viewer, UserRole.PARENT);
        Complaint c = load(viewer, id);
        requireStatus(c, EnumSet.of(ComplaintStatus.EXPLAINED), "상담을 이어");
        consultService.turn(c, req == null ? null : req.content(), req == null ? null : req.value());
        return assembler.view(c, viewer);
    }

    private static ApiException aiFailed(String complaintId) {
        return new ApiException(ErrorCode.AI_PROCESSING_FAILED,
                "AI 응답이 늦어지고 있어요. 작성하신 내용은 저장되었으니 다시 시도해 주세요.", Map.of("complaintId", complaintId));
    }

    /** POST /complaints/{id}/close — EXPLAINED → CLOSED_BY_EXPLANATION */
    @Transactional
    public ComplaintResponse close(AuthUser viewer, String id) {
        requireRole(viewer, UserRole.PARENT);
        Complaint c = load(viewer, id);
        requireStatus(c, EnumSet.of(ComplaintStatus.EXPLAINED), "종료");
        c.setClosedAt(TimeUtil.now());
        transition(c, ComplaintStatus.CLOSED_BY_EXPLANATION, viewer);
        return assembler.view(c, viewer);
    }

    /**
     * POST /complaints/{id}/submit — CARD_DRAFTING → SUBMITTED.
     * 받는 선생님(teacherId)과의 DIRECT 방을 찾거나 만들고(room.created) complaints.room_id·teacher_id 확정,
     * 카드 confirmed_at 기록, 방에 COMPLAINT_CARD 메시지 1건 (학부모와 교사가 같은 카드를 본다)
     */
    @Transactional
    public ComplaintResponse submit(AuthUser viewer, String id, SubmitRequest req, String idempotencyKey) {
        requireRole(viewer, UserRole.PARENT);
        Optional<String> dup = idempotencyService.find(viewer.id(), idempotencyKey, EP_SUBMIT);
        if (dup.isPresent()) {
            return assembler.view(load(viewer, dup.get()), viewer);
        }
        Complaint c = load(viewer, id);
        requireStatus(c, EnumSet.of(ComplaintStatus.CARD_DRAFTING), "보내기");
        String teacherId = resolveRecipient(c, req == null ? null : req.teacherId());
        SummaryCard card = latestCardOrConflict(c, req == null ? null : req.cardVersion());

        if (c.getRoomId() == null) {
            ChatRoom room = chatRoomService.ensureDirect(viewer, teacherId);
            c.setRoomId(room.getId());
            c.setTeacherId(teacherId);
        }
        var now = TimeUtil.now();
        card.setConfirmedAt(now);
        cardRepository.save(card);
        c.setSubmittedCardVersion(card.getVersion());
        c.setSubmittedAt(now);
        transition(c, ComplaintStatus.SUBMITTED, viewer);

        RoomContext ctx = roomContextService.load(roomOf(c));
        messageService.post(ctx, viewer.id(), MessageType.COMPLAINT_CARD, "민원 요약 카드를 보냈습니다.", c.getId(), card.getId(),
                null, List.of(), true);
        idempotencyService.remember(viewer.id(), idempotencyKey, EP_SUBMIT, 200, c.getId());
        return assembler.view(c, viewer);
    }

    /**
     * 받는 선생님 확인. 방에서 시작한 민원은 그 방의 선생님(생략 가능, 다르면 400),
     * 아니면 recipients 후보 중 하나여야 한다 (400 VALIDATION_ERROR + details.fields.teacherId)
     */
    private String resolveRecipient(Complaint c, String teacherId) {
        boolean blank = teacherId == null || teacherId.isBlank();
        if (c.getTeacherId() != null) {
            if (!blank && !c.getTeacherId().equals(teacherId)) {
                throw ApiException.validation("받을 선생님을 다시 골라 주세요.", "teacherId", "invalid");
            }
            return c.getTeacherId();
        }
        if (blank) {
            throw ApiException.validation("받을 선생님을 골라 주세요.", "teacherId", "required");
        }
        if (recipientService.find(userService.get(c.getStudentId()), teacherId).isEmpty()) {
            throw ApiException.validation("받을 선생님을 다시 골라 주세요.", "teacherId", "invalid");
        }
        return teacherId;
    }

    /**
     * GET /complaints/{id}/recipients — 받을 수 있는 선생님. 방에서 시작해 교사가 고정된 민원은 그 한 명만.
     * 추천: 상담에서 정한 선생님 (proposed_teacher_id), 없으면 담임
     */
    @Transactional(readOnly = true)
    public List<ComplaintRecipientResponse> recipients(AuthUser viewer, String id) {
        requireRole(viewer, UserRole.PARENT);
        Complaint c = load(viewer, id);
        User student = userService.get(c.getStudentId());
        List<ComplaintRecipientService.Candidate> candidates = recipientService.candidates(student);
        if (c.getTeacherId() != null) {
            String label = candidates.stream().filter(x -> x.teacherId().equals(c.getTeacherId()))
                    .map(ComplaintRecipientService.Candidate::label).findFirst().orElse("선생님");
            return List.of(new ComplaintRecipientResponse(userService.summary(userService.get(c.getTeacherId())), label, true,
                    "이 채팅방의 선생님이에요."));
        }
        Map<String, UserSummary> users = userService.summaries(candidates.stream().map(ComplaintRecipientService.Candidate::teacherId).toList());
        // 추천: 상담에서 정한 선생님, 상담에서 정하지 않았으면 담임
        String proposed = consultService.proposedTeacherId(c);
        boolean fromConsult = proposed != null && candidates.stream().anyMatch(x -> x.teacherId().equals(proposed));
        String pick = fromConsult ? proposed : candidates.stream().filter(ComplaintRecipientService.Candidate::homeroom)
                .map(ComplaintRecipientService.Candidate::teacherId).findFirst()
                .orElse(candidates.isEmpty() ? null : candidates.get(0).teacherId());
        return candidates.stream()
                .map(x -> new ComplaintRecipientResponse(users.get(x.teacherId()), x.label(), x.teacherId().equals(pick),
                        !x.teacherId().equals(pick) ? null
                                : fromConsult ? "상담에서 함께 정한 선생님이에요."
                                : x.homeroom() ? "담임 선생님이 먼저 상황을 확인해요." : "이 반을 맡은 선생님이에요."))
                .toList();
    }

    // =====================================================================
    // 조회
    // =====================================================================

    /** GET /complaints — 역할별 범위, createdAt 내림차순. cursor = 다음 페이지 번호 */
    @Transactional(readOnly = true)
    public ComplaintPage list(AuthUser viewer, String status, String studentId, String cursor, Integer limit) {
        int size = limit == null || limit <= 0 ? 20 : Math.min(limit, 100);
        int page = 0;
        if (cursor != null && !cursor.isBlank()) {
            try {
                page = Math.max(0, Integer.parseInt(cursor));
            } catch (NumberFormatException e) {
                throw ApiException.validation("목록 위치가 올바르지 않습니다.", "cursor", "invalid");
            }
        }
        Specification<Complaint> spec = scope(viewer);
        if (spec == null) {
            return new ComplaintPage(List.of(), null);
        }
        Set<ComplaintStatus> statuses = parseStatuses(status);
        if (!statuses.isEmpty()) {
            spec = spec.and((root, q, cb) -> root.get("status").in(statuses));
        }
        if (studentId != null && !studentId.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("studentId"), studentId));
        }
        Page<Complaint> result = complaintRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        List<ComplaintResponse> items = result.getContent().stream().map(c -> assembler.view(c, viewer, false)).toList();
        return new ComplaintPage(items, result.hasNext() ? String.valueOf(page + 1) : null);
    }

    private Specification<Complaint> scope(AuthUser viewer) {
        Specification<Complaint> school = (root, q, cb) -> cb.equal(root.get("schoolId"), viewer.schoolId());
        return switch (viewer.role()) {
            case PARENT -> school.and((root, q, cb) -> cb.equal(root.get("parentId"), viewer.id()));
            case TEACHER -> school.and((root, q, cb) -> cb.and(
                    cb.equal(root.get("teacherId"), viewer.id()),
                    cb.not(root.get("status").in(ComplaintStatus.PRE_SUBMIT))));
            case RESPONSE_TEAM, ADMIN -> school.and((root, q, cb) -> {
                Subquery<String> esc = q.subquery(String.class);
                var e = esc.from(Escalation.class);
                esc.select(e.get("complaintId")).where(cb.equal(e.get("complaintId"), root.get("id")));
                return cb.exists(esc);
            });
            default -> null;
        };
    }

    private static Set<ComplaintStatus> parseStatuses(String csv) {
        Set<ComplaintStatus> result = new LinkedHashSet<>();
        if (csv == null || csv.isBlank()) {
            return result;
        }
        for (String s : csv.split(",")) {
            if (s.isBlank()) {
                continue;
            }
            try {
                result.add(ComplaintStatus.valueOf(s.trim()));
            } catch (IllegalArgumentException e) {
                throw ApiException.validation("상태 값이 올바르지 않습니다.", "status", "invalid");
            }
        }
        return result;
    }

    /** GET /complaints/{id}. 대응팀·관리자가 원문을 열면 complaint_access_logs 에 기록 */
    @Transactional
    public ComplaintResponse get(AuthUser viewer, String id, String ip) {
        Complaint c = load(viewer, id);
        if (viewer.role() == UserRole.RESPONSE_TEAM || viewer.role() == UserRole.ADMIN) {
            ComplaintAccessLog log = new ComplaintAccessLog();
            log.setComplaintId(c.getId());
            log.setUserId(viewer.id());
            log.setAccessType(AccessType.VIEW_ORIGINAL);
            log.setIpAddress(ip == null || ip.length() > 45 ? null : ip);
            accessLogRepository.save(log);
        }
        return assembler.view(c, viewer);
    }

    // =====================================================================
    // 교사: 열람 → 답변 초안 → 답변 / 도움 요청 → 종료
    // =====================================================================

    /** POST /complaints/{id}/acknowledge — SUBMITTED → TEACHER_REVIEWING */
    @Transactional
    public ComplaintResponse acknowledge(AuthUser viewer, String id) {
        requireRole(viewer, UserRole.TEACHER);
        Complaint c = load(viewer, id);
        requireStatus(c, EnumSet.of(ComplaintStatus.SUBMITTED), "열람 처리");
        c.setAcknowledgedAt(TimeUtil.now());
        transition(c, ComplaintStatus.TEACHER_REVIEWING, viewer);
        return assembler.view(c, viewer);
    }

    /** POST /complaints/{id}/reply-drafts — 저장만 하고 발송하지 않음 */
    @Transactional
    public ReplyDraftResponse createReplyDraft(AuthUser viewer, String id, ReplyDraftRequest req) {
        requireRole(viewer, UserRole.TEACHER);
        Complaint c = load(viewer, id);
        requireStatus(c, TEACHER_ACTIVE, "답변 초안을 생성");
        String situation = req == null ? "" : nvl(req.situation());
        String reason = req == null ? "" : nvl(req.reason());
        String nextActions = req == null ? "" : nvl(req.nextActions());
        if (situation.isBlank() && reason.isBlank() && nextActions.isBlank()) {
            throw ApiException.validation("메모를 한 칸 이상 적어 주세요.", "situation", "required");
        }
        User student = userService.get(c.getStudentId());
        User teacher = userService.get(viewer.id());
        List<String> requests = c.getSubmittedCardVersion() == null ? List.of()
                : cardRepository.findByComplaintIdAndVersion(c.getId(), c.getSubmittedCardVersion()).map(SummaryCard::getRequests).orElse(List.of());

        AiModels.ReplyDraftResult r;
        try {
            r = aiClient.draftReply(new AiModels.ReplyDraftInput(situation, reason, nextActions, nickname(student.getName()),
                    teacher.getName(), requests));
        } catch (AiException e) {
            throw new ApiException(ErrorCode.AI_PROCESSING_FAILED, "답변 초안을 만들지 못했어요. 잠시 후 다시 시도해 주세요.");
        }
        TeacherReplyDraft draft = new TeacherReplyDraft();
        draft.setComplaintId(c.getId());
        draft.setTeacherId(viewer.id());
        draft.setInputSituation(situation);
        draft.setInputReason(reason);
        draft.setInputNextActions(nextActions);
        draft.setDraftText(r.draft());
        draft.setModelName(aiClient.modelName());
        draftRepository.save(draft);
        return new ReplyDraftResponse(r.draft(), new ReplyDraftResponse.Sections(r.situation(), r.reason(), r.nextActions()));
    }

    /** POST /complaints/{id}/reply — TEACHER_REVIEWING → IN_CONVERSATION. 방에 COMPLAINT_REPLY 메시지 */
    @Transactional
    public ReplyResultResponse reply(AuthUser viewer, String id, ReplyRequest req) {
        requireRole(viewer, UserRole.TEACHER);
        Complaint c = load(viewer, id);
        requireStatus(c, EnumSet.of(ComplaintStatus.TEACHER_REVIEWING), "답변");
        String content = req == null ? "" : nvl(req.content()).trim();
        ContactMethod method = req == null ? null : req.contactMethod();
        Map<String, String> errors = new LinkedHashMap<>();
        if (content.isEmpty()) {
            errors.put("content", "보낼 내용을 적어 주세요.");
        } else if (content.length() > MAX_TEXT) {
            errors.put("content", "2,000자까지 보낼 수 있어요.");
        }
        if (method == null) {
            errors.put("contactMethod", "연락 방법을 골라 주세요.");
        } else if (method != ContactMethod.MESSAGE && req.scheduledAt() == null) {
            errors.put("scheduledAt", "안심전화·면담 시각을 정해 주세요.");
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        if (replyRepository.existsById(c.getId())) {
            throw new ApiException(ErrorCode.INVALID_COMPLAINT_STATUS, "이미 답변을 보냈습니다.", Map.of("status", c.getStatus().name()));
        }

        var scheduledAt = method == ContactMethod.MESSAGE ? null : req.scheduledAt();
        RoomContext ctx = roomContextService.load(roomOf(c));
        var message = messageService.post(ctx, viewer.id(), MessageType.COMPLAINT_REPLY, content, c.getId(), null, null, List.of(), true);

        ComplaintReply reply = new ComplaintReply();
        reply.setComplaintId(c.getId());
        reply.setTeacherId(viewer.id());
        reply.setMessageId(message.getId());
        reply.setContent(content);
        reply.setContactMethod(method);
        reply.setScheduledAt(scheduledAt);
        reply.setSentAt(message.getCreatedAt());
        ReplyResultResponse.Reservation safeCall = null;
        ReplyResultResponse.Reservation visit = null;
        if (method == ContactMethod.CALL) {
            reply.setSafeCallReservationId(reservationClient.reserveSafeCall(viewer.id(), c.getParentId(), scheduledAt));
            safeCall = new ReplyResultResponse.Reservation(reply.getSafeCallReservationId(), scheduledAt);
        } else if (method == ContactMethod.VISIT) {
            reply.setVisitReservationId(reservationClient.reserveVisit(viewer.id(), c.getParentId(), scheduledAt));
            visit = new ReplyResultResponse.Reservation(reply.getVisitReservationId(), scheduledAt);
        }
        replyRepository.save(reply);

        c.setRepliedAt(message.getCreatedAt());
        transition(c, ComplaintStatus.IN_CONVERSATION, viewer);
        return new ReplyResultResponse(c.getStatus(), message.getId(), safeCall, visit);
    }

    /** POST /complaints/{id}/escalations — 민원대응팀 도움 요청 → ESCALATED */
    @Transactional
    public EscalationResponse escalate(AuthUser viewer, String id, EscalationRequest req) {
        requireRole(viewer, UserRole.TEACHER);
        Complaint c = load(viewer, id);
        if (escalationRepository.findByComplaintId(c.getId()).isPresent()) {
            throw new ApiException(ErrorCode.INVALID_COMPLAINT_STATUS, "이미 민원대응팀에 도움을 요청했습니다.",
                    Map.of("status", c.getStatus().name()));
        }
        requireStatus(c, TEACHER_ACTIVE, "도움 요청");

        List<String> reasons = req == null || req.reasonTypes() == null ? List.of() : req.reasonTypes();
        String description = req == null ? "" : nvl(req.description()).trim();
        Map<String, String> errors = new LinkedHashMap<>();
        if (reasons.isEmpty()) {
            errors.put("reasonTypes", "사유를 하나 이상 골라 주세요.");
        } else if (!reasons.stream().allMatch(r -> isEnum(EscalationReason.class, r))) {
            errors.put("reasonTypes", "사유 값이 올바르지 않습니다.");
        }
        if (description.isEmpty()) {
            errors.put("description", "상황 설명을 적어 주세요.");
        } else if (description.length() > MAX_TEXT) {
            errors.put("description", "2,000자까지 쓸 수 있어요.");
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }

        var now = TimeUtil.now();
        Escalation e = new Escalation();
        e.setId(IdGenerator.newId(IdGenerator.ESCALATION));
        e.setSchoolId(c.getSchoolId());
        e.setComplaintId(c.getId());
        e.setRequestedBy(viewer.id());
        e.setReasonTypes(new ArrayList<>(new LinkedHashSet<>(reasons)));
        e.setDescription(description);
        e.setRequestedAt(now);
        e = escalationRepository.save(e);

        EscalationLog log = new EscalationLog();
        log.setEscalationId(e.getId());
        log.setAction(EscalationAction.CREATED);
        log.setToStage(e.getStage());
        log.setToStatus(e.getStatus());
        log.setActorId(viewer.id());
        escalationLogRepository.save(log);

        c.setEscalatedAt(now);
        transition(c, ComplaintStatus.ESCALATED, viewer);

        EscalationResponse body = assembler.escalation(e);
        publisher.publish(teamMemberIds(c.getSchoolId()), RealtimeEvents.ESCALATION_CREATED, body);
        // TODO: 대응팀 푸시 알림(notifications), 학부모 안내 여부는 미결 (DB 설계서 8장)
        return body;
    }

    /** POST /complaints/{id}/resolve — IN_CONVERSATION·ESCALATED → RESOLVED */
    @Transactional
    public ComplaintResponse resolve(AuthUser viewer, String id) {
        requireRole(viewer, UserRole.TEACHER, UserRole.RESPONSE_TEAM, UserRole.ADMIN);
        Complaint c = load(viewer, id);
        requireStatus(c, EnumSet.of(ComplaintStatus.IN_CONVERSATION, ComplaintStatus.ESCALATED), "처리 종료");
        var now = TimeUtil.now();
        c.setResolvedAt(now);

        escalationRepository.findByComplaintId(c.getId()).ifPresent(e -> {
            if (e.getStatus() != EscalationStatus.RESOLVED) {
                EscalationStatus from = e.getStatus();
                e.setStatus(EscalationStatus.RESOLVED);
                e.setResolvedAt(now);
                escalationRepository.save(e);
                EscalationLog log = new EscalationLog();
                log.setEscalationId(e.getId());
                log.setAction(EscalationAction.STATUS_CHANGED);
                log.setFromStatus(from);
                log.setToStatus(EscalationStatus.RESOLVED);
                log.setActorId(viewer.id());
                escalationLogRepository.save(log);
                Set<String> targets = new LinkedHashSet<>();
                targets.add(e.getRequestedBy());
                if (e.getAssigneeId() != null) {
                    targets.add(e.getAssigneeId());
                }
                publisher.publish(targets, RealtimeEvents.ESCALATION_UPDATED, assembler.escalation(e));
            }
        });
        transition(c, ComplaintStatus.RESOLVED, viewer);
        return assembler.view(c, viewer);
    }

    // =====================================================================
    // 공통 (SummaryCardService 도 사용)
    // =====================================================================

    /**
     * 볼 수 있는 민원만 (타 학교·권한 밖은 404).
     * 학부모: 본인이 쓴 민원 / 교사: 본인이 받은 민원 중 SUBMITTED 이후 / 대응팀·관리자: 학교 전체 중 이관된 민원
     */
    public Complaint load(AuthUser viewer, String id) {
        Complaint c = complaintRepository.findById(nvl(id))
                .filter(x -> x.getSchoolId().equals(viewer.schoolId()))
                .orElseThrow(() -> ApiException.notFound("민원을 찾을 수 없습니다."));
        boolean visible = switch (viewer.role()) {
            case PARENT -> c.getParentId().equals(viewer.id());
            case TEACHER -> viewer.id().equals(c.getTeacherId()) && !c.getStatus().isPreSubmit();
            case RESPONSE_TEAM, ADMIN -> escalationRepository.findByComplaintId(c.getId()).isPresent();
            default -> false;
        };
        if (!visible) {
            throw ApiException.notFound("민원을 찾을 수 없습니다.");
        }
        return c;
    }

    public void requireRole(AuthUser viewer, UserRole... roles) {
        if (Arrays.stream(roles).noneMatch(r -> r == viewer.role())) {
            throw ApiException.forbidden("이 기능을 쓸 권한이 없습니다.");
        }
    }

    public void requireStatus(Complaint c, Set<ComplaintStatus> allowed, String action) {
        if (!allowed.contains(c.getStatus())) {
            throw new ApiException(ErrorCode.INVALID_COMPLAINT_STATUS,
                    "지금은 " + action + "할 수 없어요. (현재 상태: " + label(c.getStatus()) + ")",
                    Map.of("status", c.getStatus().name()));
        }
    }

    /** 카드 버전 확인. baseVersion·cardVersion 이 최신이 아니면 409 + details.latestVersion */
    public SummaryCard latestCardOrConflict(Complaint c, Integer version) {
        SummaryCard latest = cardRepository.findFirstByComplaintIdOrderByVersionDesc(c.getId()).orElse(null);
        if (latest == null || version == null || latest.getVersion() != version) {
            Map<String, Object> details = new LinkedHashMap<>();
            if (latest != null) {
                details.put("latestVersion", latest.getVersion());
            }
            throw new ApiException(ErrorCode.INVALID_COMPLAINT_STATUS, "카드가 그 사이 바뀌었습니다. 최신 카드를 다시 불러와 주세요.", details);
        }
        return latest;
    }

    /**
     * 상태 전이: 저장(낙관적 잠금 version 확인) + 이력 + complaint.status_changed.
     * 받는 사람: 학부모, (제출 이후면) 교사, (이관되었으면) 대응팀
     */
    public void transition(Complaint c, ComplaintStatus to, AuthUser actor) {
        ComplaintStatus from = c.getStatus();
        c.setStatus(to);
        complaintRepository.saveAndFlush(c);
        history(c.getId(), from, to, actor);

        Set<String> targets = new LinkedHashSet<>();
        targets.add(c.getParentId());
        if (!to.isPreSubmit() && c.getTeacherId() != null) {
            targets.add(c.getTeacherId());
        }
        if (to == ComplaintStatus.ESCALATED || c.getEscalatedAt() != null) {
            targets.addAll(teamMemberIds(c.getSchoolId()));
        }
        publisher.publish(targets, RealtimeEvents.COMPLAINT_STATUS_CHANGED,
                new RealtimeEvents.StatusChanged(c.getId(), from, to, TimeUtil.now()));
    }

    private void history(String complaintId, ComplaintStatus from, ComplaintStatus to, AuthUser actor) {
        ComplaintStatusHistory h = new ComplaintStatusHistory();
        h.setComplaintId(complaintId);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setActorId(actor == null ? null : actor.id());
        h.setActorRole(actor == null ? null : actor.role());
        historyRepository.save(h);
    }

    private List<String> teamMemberIds(String schoolId) {
        return teamRepository.findBySchoolIdAndActiveTrue(schoolId).stream().map(ResponseTeamMember::getUserId).toList();
    }

    private ChatRoom roomOf(Complaint c) {
        return Optional.ofNullable(c.getRoomId()).flatMap(roomRepository::findById)
                .orElseThrow(() -> ApiException.notFound("채팅방을 찾을 수 없습니다."));
    }

    /**
     * AI 에 보낼 가명처리 원문. 학생·학부모 이름과 학번을 가린다.
     * TODO(AI): 학년·반, 전화번호 등 개인정보 패턴까지 가리는 가명처리 모듈로 교체
     */
    public String maskedText(Complaint c) {
        return masker.mask(c, cipher.decrypt(c.getOriginalTextEnc()));
    }

    /** 부르는 이름: 성을 빼고, 끝 글자에 받침이 있으면 '이' 를 붙인다 (김민수 → 민수, 김리현 → 리현이) */
    static String nickname(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "";
        }
        String given = fullName.length() > 1 ? fullName.substring(1) : fullName;
        char last = given.charAt(given.length() - 1);
        boolean batchim = last >= 0xAC00 && last <= 0xD7A3 && (last - 0xAC00) % 28 != 0;
        return batchim ? given + "이" : given;
    }

    private static String label(ComplaintStatus s) {
        return switch (s) {
            case EXPLAINED -> "AI 설명 확인 중";
            case CLOSED_BY_EXPLANATION -> "설명으로 종료";
            case CARD_DRAFTING -> "요약 카드 작성 중";
            case SUBMITTED -> "선생님께 전달됨";
            case TEACHER_REVIEWING -> "선생님 확인 중";
            case IN_CONVERSATION -> "대화 중";
            case ESCALATED -> "민원대응팀 처리 중";
            case RESOLVED -> "처리 완료";
        };
    }

    static <E extends Enum<E>> boolean isEnum(Class<E> type, String value) {
        try {
            Enum.valueOf(type, value);
            return true;
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    public record ComplaintPage(List<ComplaintResponse> items, String nextCursor) {
    }
}
