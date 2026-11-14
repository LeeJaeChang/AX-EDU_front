package com.axedu.dto.complaint;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/** AI 상담 대화 한 줄 (학부모 본인에게만. 교사 응답에는 내려가지 않는다) */
@Schema(description = "AI 상담 대화 한 줄")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConsultMessageResponse(
        String id,
        @Schema(description = "PARENT | AI") String from,
        @Schema(description = "본문. 줄바꿈으로 문단 구분") String content,
        @Schema(description = "왜 묻는지 등 보조 설명 (회색 글씨)") String note,
        @Schema(description = "사실관계 정리표") List<Fact> facts,
        @Schema(description = "규정·법령 설명") AiExplanationResponse explanation,
        @Schema(description = "연락할 선생님 추천 근거") List<String> reasoning,
        @Schema(description = "빠른 답 (마지막 AI 메시지의 것만 버튼으로 표시)") List<QuickReply> quickReplies,
        @Schema(description = "학부모가 빠른 답으로 보낸 값") String value,
        OffsetDateTime createdAt) {

    public record Fact(String label, String value) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuickReply(String label, String value) {

        public static QuickReply of(String label) {
            return new QuickReply(label, null);
        }
    }
}
