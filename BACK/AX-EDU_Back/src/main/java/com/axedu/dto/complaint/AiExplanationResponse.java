package com.axedu.dto.complaint;

import java.util.List;

import com.axedu.dto.regulation.RegulationResponse;
import com.axedu.entity.enums.AiRecommendation;

/** AiExplanation (계약서 2.9) */
public record AiExplanationResponse(
        String empathy,
        String reason,
        String alternative,
        List<RegulationResponse> citations,
        AiRecommendation recommendation) {
}
