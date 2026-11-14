package com.axedu.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.axedu.common.response.ApiResponse;
import com.axedu.common.response.PageMeta;
import com.axedu.common.security.AuthUser;
import com.axedu.common.security.CurrentUser;
import com.axedu.config.WebConfig;
import com.axedu.dto.complaint.ComplaintRecipientResponse;
import com.axedu.dto.complaint.ComplaintResponse;
import com.axedu.dto.complaint.ConsultRequest;
import com.axedu.dto.complaint.CreateComplaintRequest;
import com.axedu.dto.complaint.EditCardRequest;
import com.axedu.dto.complaint.EscalationRequest;
import com.axedu.dto.complaint.EscalationResponse;
import com.axedu.dto.complaint.ReplyDraftRequest;
import com.axedu.dto.complaint.ReplyDraftResponse;
import com.axedu.dto.complaint.ReplyRequest;
import com.axedu.dto.complaint.ReplyResultResponse;
import com.axedu.dto.complaint.RevisionRequest;
import com.axedu.dto.complaint.SubmitRequest;
import com.axedu.dto.complaint.SummaryCardResponse;
import com.axedu.service.ComplaintService;
import com.axedu.service.SummaryCardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 민원 (계약서 4장). 상태 변경 API 는 모두 전체 Complaint, 카드 API 는 카드 자체, 도움 요청은 전체 Escalation 을 data 로.
 *
 * <p>상태 흐름: EXPLAINED → (CLOSED_BY_EXPLANATION | CARD_DRAFTING → SUBMITTED → TEACHER_REVIEWING → IN_CONVERSATION)
 * → (ESCALATED) → RESOLVED
 */
@RestController
@RequestMapping(WebConfig.API_BASE + "/complaints")
@RequiredArgsConstructor
public class ComplaintController {

    private static final String IDEMPOTENCY = "Idempotency-Key";

    private static final String TAG_PARENT = "민원 - 학부모";
    private static final String TAG_QUERY = "민원 - 조회";
    private static final String TAG_TEACHER = "민원 - 교사·대응팀";

    private final ComplaintService complaintService;
    private final SummaryCardService summaryCardService;

    // ---------------- 학부모 ----------------

    @Tag(name = TAG_PARENT, description = "학부모: 민원 작성 → AI 규정 설명 → 요약 카드 작성·수정 → 교사에게 보내기")
    @Operation(summary = "민원 작성 (학부모)",
            description = "민원 원문을 등록하고 AI 규정 설명을 받습니다. 성공하면 상태 `EXPLAINED`. "
                    + "`roomId` 는 선택: 학부모 첫 화면에서 시작하면 비우고(`teacher`·`roomId` 가 null) 받는 선생님은 제출할 때 고릅니다. "
                    + "AI 실패 시 422 `AI_PROCESSING_FAILED` + `details.complaintId` → `/explanation/retry` 로 재시도. "
                    + "로컬 스텁은 원문에 `[AI실패]` 를 넣으면 첫 시도 실패를 재현합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "생성됨 (EXPLAINED)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "AI_PROCESSING_FAILED — details.complaintId",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    @PostMapping
    public ResponseEntity<ApiResponse<ComplaintResponse>> create(@CurrentUser AuthUser user,
                                                                 @Parameter(description = "중복 제출 방지 키 (선택)") @RequestHeader(name = IDEMPOTENCY, required = false) String key,
                                                                 @RequestBody CreateComplaintRequest req) {
        return created(complaintService.create(user, req, key));
    }

    @Tag(name = TAG_PARENT)
    @Operation(summary = "상담 첫 응답 재시도 (학부모)",
            description = "민원 작성 때 AI 첫 응답(공감 + 첫 질문)이 실패해 `consultStage` 가 null 이면 다시 만듭니다. 이미 시작했으면 그대로 돌려줍니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "AI_PROCESSING_FAILED",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    @PostMapping("/{id}/explanation/retry")
    public ApiResponse<ComplaintResponse> retry(@CurrentUser AuthUser user, @Parameter(description = "민원 ID") @PathVariable String id) {
        return ApiResponse.ok(complaintService.retryExplanation(user, id));
    }

    @Tag(name = TAG_PARENT)
    @Operation(summary = "AI 상담 이어 가기 (학부모)",
            description = "학부모의 답(자유 입력 또는 빠른 답 `value`) 하나에 똑똑 AI 상담사가 다음 질문·정리·규정 설명을 돌려줍니다. "
                    + "단계: FACTS(대응 시급도→일시→장소→자세한 이야기→아이 상태→바라는 점) → EXPLAIN(정리표 + 규정·법령) → "
                    + "RECIPIENT(선생님 추천) → CONTACT(방법·시간) → READY. `status` 는 EXPLAINED 그대로, `consultStage` 만 바뀝니다. "
                    + "`value` 가 있으면 `content` 보다 우선합니다 (CLOSE·MAKE_CARD 는 보내지 않고 /close·/summary-card 를 호출).")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공 — consultation 에 학부모 메시지 1개 + AI 메시지가 덧붙음")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — content 1~2000자, 상담 메시지 40개 초과",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "INVALID_COMPLAINT_STATUS — EXPLAINED 가 아님",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "AI_PROCESSING_FAILED — 첫 응답 전 (details.complaintId → retry)",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    @PostMapping("/{id}/consultations")
    public ApiResponse<ComplaintResponse> consult(@CurrentUser AuthUser user, @Parameter(description = "민원 ID") @PathVariable String id,
                                                  @RequestBody ConsultRequest req) {
        return ApiResponse.ok(complaintService.consult(user, id, req));
    }

    @Tag(name = TAG_PARENT)
    @Operation(summary = "설명으로 종료 (학부모)", description = "AI 설명으로 충분할 때 민원을 닫습니다. `EXPLAINED` → `CLOSED_BY_EXPLANATION`.")
    @PostMapping("/{id}/close")
    public ApiResponse<ComplaintResponse> close(@CurrentUser AuthUser user, @Parameter(description = "민원 ID") @PathVariable String id) {
        return ApiResponse.ok(complaintService.close(user, id));
    }

    @Tag(name = TAG_PARENT)
    @Operation(summary = "요약 카드 생성 (학부모)",
            description = "상담이 `READY` 단계일 때 AI 가 상담 전체(원문 + 상담에서 확인한 사실·바라는 점·연락 방법 + 규정)를 교사용 요약 카드로 정리합니다. "
                    + "`EXPLAINED` → `CARD_DRAFTING`, `consultStage` 는 null. READY 가 아니면 409 + `details.stage`. "
                    + "카드의 `urgency` 는 상담에서 정한 대응 시급도, `parentEmotion` 은 교사·대응팀에게만 내려갑니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "카드 생성됨")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "INVALID_COMPLAINT_STATUS — 상담이 READY 가 아님 (details.stage)",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "AI_PROCESSING_FAILED",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    @PostMapping("/{id}/summary-card")
    public ResponseEntity<ApiResponse<SummaryCardResponse>> createCard(@CurrentUser AuthUser user,
                                                                       @Parameter(description = "민원 ID") @PathVariable String id) {
        return created(summaryCardService.create(user, id));
    }

    @Tag(name = TAG_PARENT)
    @Operation(summary = "요약 카드 AI 수정 (학부모)",
            description = "자연어 지시(`instruction`)로 카드를 고칩니다. '긴급으로 바꿔 주세요'·'급하지 않아요'로 `urgency` 도 바꿀 수 있습니다. "
                    + "`baseVersion` 이 최신이 아니면 409 + `details.latestVersion`.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "INVALID_COMPLAINT_STATUS — 상태 불가 또는 카드 버전 충돌",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    @PostMapping("/{id}/summary-card/revisions")
    public ApiResponse<SummaryCardResponse> revise(@CurrentUser AuthUser user, @Parameter(description = "민원 ID") @PathVariable String id,
                                                   @RequestBody RevisionRequest req) {
        return ApiResponse.ok(summaryCardService.revise(user, id, req));
    }

    @Tag(name = TAG_PARENT)
    @Operation(summary = "요약 카드 직접 수정 (학부모)",
            description = "보낸 키만 바꿉니다. 태그는 `basicInfo.tags` 로 보냅니다. `urgency`·`parentEmotion` 은 고칠 수 없고 이전 버전 값을 이어받습니다. "
                    + "`baseVersion` 이 최신이 아니면 409 + `details.latestVersion`.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "INVALID_COMPLAINT_STATUS — 상태 불가 또는 카드 버전 충돌",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    @PatchMapping("/{id}/summary-card")
    public ApiResponse<SummaryCardResponse> edit(@CurrentUser AuthUser user, @Parameter(description = "민원 ID") @PathVariable String id,
                                                 @RequestBody EditCardRequest req) {
        return ApiResponse.ok(summaryCardService.edit(user, id, req));
    }

    @Tag(name = TAG_PARENT)
    @Operation(summary = "받을 수 있는 선생님 (학부모)",
            description = "학생의 이번 학년도 담임 + 교과 교사. `recommended` 가 기본 선택(지금은 담임, AI 상담 도입 후 상담에서 정한 선생님). "
                    + "방에서 시작해 교사가 고정된 민원은 그 한 명만 돌려줍니다.")
    @GetMapping("/{id}/recipients")
    public ApiResponse<List<ComplaintRecipientResponse>> recipients(@CurrentUser AuthUser user,
                                                                    @Parameter(description = "민원 ID") @PathVariable String id) {
        return ApiResponse.ok(complaintService.recipients(user, id));
    }

    @Tag(name = TAG_PARENT)
    @Operation(summary = "확인하고 보내기 (학부모)",
            description = "확인한 카드 버전(`cardVersion`)을 고른 선생님(`teacherId`)에게 보냅니다. `CARD_DRAFTING` → `SUBMITTED`. "
                    + "학부모–선생님 DIRECT 방을 찾고 없으면 만든 뒤(`room.created`) 카드 메시지를 올립니다. "
                    + "`teacherId` 가 후보가 아니거나 방의 선생님과 다르면 400 `details.fields.teacherId`.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — teacherId 누락·후보 아님",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "INVALID_COMPLAINT_STATUS — 상태 불가 또는 카드 버전 충돌",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    @PostMapping("/{id}/submit")
    public ApiResponse<ComplaintResponse> submit(@CurrentUser AuthUser user, @Parameter(description = "민원 ID") @PathVariable String id,
                                                 @Parameter(description = "중복 제출 방지 키 (선택)") @RequestHeader(name = IDEMPOTENCY, required = false) String key,
                                                 @RequestBody SubmitRequest req) {
        return ApiResponse.ok(complaintService.submit(user, id, req, key));
    }

    // ---------------- 조회 ----------------

    @Tag(name = TAG_QUERY, description = "민원 목록·상세 (역할별로 보이는 범위와 필드가 다름)")
    @Operation(summary = "민원 목록",
            description = "학부모는 내 민원, 교사는 제출된(SUBMITTED 이후) 내 반 민원을 봅니다. `meta.nextCursor` 로 다음 페이지.")
    @GetMapping
    public ApiResponse<List<ComplaintResponse>> list(@CurrentUser AuthUser user,
                                                     @Parameter(description = "상태 필터 (쉼표로 여러 개)", example = "SUBMITTED,TEACHER_REVIEWING")
                                                     @RequestParam(required = false) String status,
                                                     @Parameter(description = "학생 ID 필터", example = "usr_s1") @RequestParam(required = false) String studentId,
                                                     @Parameter(description = "이전 응답의 meta.nextCursor") @RequestParam(required = false) String cursor,
                                                     @Parameter(description = "페이지 크기 (기본 20, 최대 100)", example = "20") @RequestParam(required = false) Integer limit) {
        ComplaintService.ComplaintPage page = complaintService.list(user, status, studentId, cursor, limit);
        return ApiResponse.ok(page.items(), PageMeta.cursor(page.nextCursor()));
    }

    @Tag(name = TAG_QUERY)
    @Operation(summary = "민원 상세",
            description = "교사에게는 `originalText`·`aiExplanation` 키를 빼고, 제출된 카드 버전을 보여 줍니다. 열람 기록이 남습니다.")
    @GetMapping("/{id}")
    public ApiResponse<ComplaintResponse> get(@CurrentUser AuthUser user,
                                              @Parameter(description = "민원 ID", example = "cmp_seed1") @PathVariable String id,
                                              HttpServletRequest request) {
        return ApiResponse.ok(complaintService.get(user, id, request.getRemoteAddr()));
    }

    // ---------------- 교사 ----------------

    @Tag(name = TAG_TEACHER, description = "교사: 열람 → 답변 초안 → 답변 / 민원대응팀 도움 요청 / 처리 종료")
    @Operation(summary = "열람 처리 (교사)", description = "`SUBMITTED` → `TEACHER_REVIEWING`.")
    @PostMapping("/{id}/acknowledge")
    public ApiResponse<ComplaintResponse> acknowledge(@CurrentUser AuthUser user,
                                                      @Parameter(description = "민원 ID", example = "cmp_seed1") @PathVariable String id) {
        return ApiResponse.ok(complaintService.acknowledge(user, id));
    }

    @Tag(name = TAG_TEACHER)
    @Operation(summary = "답변 초안 생성 (교사)", description = "상황·사유·다음 조치를 주면 AI 가 학부모용 답변 초안을 씁니다. 상태는 바뀌지 않습니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "AI_PROCESSING_FAILED",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    @PostMapping("/{id}/reply-drafts")
    public ApiResponse<ReplyDraftResponse> replyDraft(@CurrentUser AuthUser user,
                                                      @Parameter(description = "민원 ID", example = "cmp_seed1") @PathVariable String id,
                                                      @RequestBody ReplyDraftRequest req) {
        return ApiResponse.ok(complaintService.createReplyDraft(user, id, req));
    }

    @Tag(name = TAG_TEACHER)
    @Operation(summary = "답변 보내기 (교사)",
            description = "답변을 채팅방에 보내고 연락 방법(`MESSAGE`/`CALL`/`VISIT`)을 기록합니다. `TEACHER_REVIEWING` → `IN_CONVERSATION`.")
    @PostMapping("/{id}/reply")
    public ApiResponse<ReplyResultResponse> reply(@CurrentUser AuthUser user,
                                                  @Parameter(description = "민원 ID", example = "cmp_seed1") @PathVariable String id,
                                                  @RequestBody ReplyRequest req) {
        return ApiResponse.ok(complaintService.reply(user, id, req));
    }

    @Tag(name = TAG_TEACHER)
    @Operation(summary = "민원대응팀 도움 요청 (교사)",
            description = "`SUBMITTED`·`TEACHER_REVIEWING`·`IN_CONVERSATION` 에서 가능. → `ESCALATED`. 이미 요청했으면 409.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "도움 요청 생성됨")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "INVALID_COMPLAINT_STATUS — 상태 불가 또는 이미 요청함",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    @PostMapping("/{id}/escalations")
    public ResponseEntity<ApiResponse<EscalationResponse>> escalate(@CurrentUser AuthUser user,
                                                                    @Parameter(description = "민원 ID", example = "cmp_seed1") @PathVariable String id,
                                                                    @RequestBody EscalationRequest req) {
        return created(complaintService.escalate(user, id, req));
    }

    @Tag(name = TAG_TEACHER)
    @Operation(summary = "처리 종료 (교사·대응팀·관리자)", description = "`IN_CONVERSATION`·`ESCALATED` → `RESOLVED`. 도움 요청도 함께 종료됩니다.")
    @PostMapping("/{id}/resolve")
    public ApiResponse<ComplaintResponse> resolve(@CurrentUser AuthUser user,
                                                  @Parameter(description = "민원 ID", example = "cmp_seed1") @PathVariable String id) {
        return ApiResponse.ok(complaintService.resolve(user, id));
    }

    private static <T> ResponseEntity<ApiResponse<T>> created(T body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(body));
    }
}
