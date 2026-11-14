package com.axedu.dto.complaint;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "답변 초안 요청 (교사)")
public record ReplyDraftRequest(
        @Schema(description = "확인한 상황", example = "수행평가 채점 기준표를 다시 확인했습니다.") String situation,
        @Schema(description = "사유·설명", example = "발표 항목에서 시간 초과로 감점되었습니다.") String reason,
        @Schema(description = "다음 조치", example = "다음 주 상담 시간에 채점표를 함께 보시면 좋겠습니다.") String nextActions) {
}
