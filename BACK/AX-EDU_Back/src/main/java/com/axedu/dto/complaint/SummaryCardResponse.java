package com.axedu.dto.complaint;

import java.time.OffsetDateTime;
import java.util.List;

import com.axedu.dto.chat.TimeRange;
import com.axedu.dto.regulation.RegulationResponse;
import com.axedu.entity.value.CardSuggestion;
import com.axedu.entity.value.ParentEmotion;
import com.axedu.entity.value.SituationItem;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * SummaryCard / SummaryCardResult (계약서 2.10).
 * suggestions 는 카드 API 응답에만, 값이 있을 때만 키를 넣는다. Complaint.summaryCard 안에서는 null → 키 생략.
 */
public record SummaryCardResponse(
        int version,
        BasicInfo basicInfo,
        String attribution,
        List<SituationItem> situation,
        List<String> concerns,
        List<String> requests,
        PreferredContact preferredContact,
        List<RegulationResponse> regulations,
        CardUrgency urgency,
        /** 학부모 감정 상태 — 교사·민원대응팀에게만. 학부모 응답에서는 키 자체를 뺀다 (null → 생략) */
        @JsonInclude(JsonInclude.Include.NON_NULL) ParentEmotion parentEmotion,
        OffsetDateTime confirmedByParentAt,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<CardSuggestion> suggestions) {

    public record BasicInfo(int grade, int classNo, OffsetDateTime receivedAt, List<String> tags) {
    }

    public record PreferredContact(List<String> methods, TimeRange timeWindow) {
    }

    public SummaryCardResponse withoutSuggestions() {
        return new SummaryCardResponse(version, basicInfo, attribution, situation, concerns, requests, preferredContact,
                regulations, urgency, parentEmotion, confirmedByParentAt, null);
    }
}
