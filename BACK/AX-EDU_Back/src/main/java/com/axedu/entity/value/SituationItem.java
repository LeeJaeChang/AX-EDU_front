package com.axedu.entity.value;

/** 요약 카드 상황 항목 (summary_cards.situation JSONB 원소) */
public record SituationItem(Integer order, String when, String where, String what) {

    public SituationItem withOrder(int newOrder) {
        return new SituationItem(newOrder, when, where, what);
    }
}
