package com.axedu.dto.regulation;

import com.axedu.entity.enums.SourceType;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Regulation (계약서 2.8). documentUrl 은 없으면 키 생략 */
public record RegulationResponse(
        String id,
        SourceType sourceType,
        String title,
        String excerpt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String documentUrl) {
}
