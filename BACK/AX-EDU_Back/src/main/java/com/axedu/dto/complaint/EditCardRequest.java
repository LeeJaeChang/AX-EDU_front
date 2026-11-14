package com.axedu.dto.complaint;

import java.util.List;

import com.axedu.entity.value.SituationItem;

/** PATCH /complaints/{id}/summary-card. 보낸 키만 바꾼다. 태그는 basicInfo.tags 로 온다 */
public record EditCardRequest(
        Integer baseVersion,
        List<SituationItem> situation,
        List<String> concerns,
        List<String> requests,
        SummaryCardResponse.PreferredContact preferredContact,
        BasicInfoPatch basicInfo) {

    public record BasicInfoPatch(List<String> tags) {
    }
}
