package com.axedu.dto.complaint;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.axedu.dto.user.UserSummary;
import com.axedu.entity.enums.ComplaintStatus;
import com.axedu.entity.enums.ComplaintTag;
import com.axedu.entity.enums.ConsultStage;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Complaint (계약서 2.13).
 * originalText·aiExplanation 은 교사에게는 키 자체를 빼야 하고(필드 = null → 생략),
 * 학부모에게는 값이 없어도 "aiExplanation": null 로 내려야 한다(Optional.empty() → null).
 */
public record ComplaintResponse(
        String id,
        ComplaintStatus status,
        UserSummary parent,
        UserSummary teacher,
        UserSummary student,
        String roomId,
        @JsonInclude(JsonInclude.Include.NON_NULL) Optional<String> originalText,
        @JsonInclude(JsonInclude.Include.NON_NULL) Optional<AiExplanationResponse> aiExplanation,
        /** AI 상담 대화 (학부모 본인·대응팀). 교사·목록 응답에서는 키 생략 */
        @JsonInclude(JsonInclude.Include.NON_NULL) List<ConsultMessageResponse> consultation,
        /** 지금 상담 단계 (학부모 본인, 카드 단계 이후 null). 교사에게는 키 생략 */
        @JsonInclude(JsonInclude.Include.NON_NULL) Optional<ConsultStage> consultStage,
        /** 상담에서 정한 받는 선생님 (학부모 본인). 교사에게는 키 생략 */
        @JsonInclude(JsonInclude.Include.NON_NULL) Optional<UserSummary> proposedTeacher,
        /** 학부모가 처음에 고른 사전 분류 (학부모 본인). 교사에게는 키 생략 */
        @JsonInclude(JsonInclude.Include.NON_NULL) Optional<ComplaintTag> category,
        SummaryCardResponse summaryCard,
        TeacherReplyResponse teacherReply,
        EscalationResponse escalation,
        OffsetDateTime createdAt,
        OffsetDateTime submittedAt,
        OffsetDateTime closedAt) {
}
