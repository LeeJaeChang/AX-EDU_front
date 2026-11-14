package com.axedu.entity.value;

/** 감정 표현 재유입 시 AI 순화 제안 (summary_cards.suggestions JSONB 원소). field: situation|concerns|requests */
public record CardSuggestion(String field, Integer index, String original, String suggested) {
}
