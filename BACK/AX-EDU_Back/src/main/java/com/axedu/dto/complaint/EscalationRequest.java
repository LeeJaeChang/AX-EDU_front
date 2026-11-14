package com.axedu.dto.complaint;

import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "민원대응팀 도움 요청 (교사)")
public record EscalationRequest(
        @ArraySchema(schema = @Schema(allowableValues = {"REPEATED_DEMAND", "ABUSE", "OUT_OF_HOURS_PRESSURE", "BEYOND_AUTHORITY", "ETC"}),
                arraySchema = @Schema(description = "도움 요청 사유", example = "[\"REPEATED_DEMAND\"]")) List<String> reasonTypes,
        @Schema(description = "상황 설명", example = "같은 요구가 일주일째 반복되고 있습니다.") String description) {
}
