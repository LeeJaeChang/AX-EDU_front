package com.axedu.entity.value;

import java.util.List;

/**
 * 상담 중 학부모 감정 상태 (summary_cards.parent_emotion JSONB). 교사·민원대응팀에게만 내려간다.
 *
 * @param level   CALM 차분함 / CONCERNED 걱정·속상함 / AGITATED 격양됨
 * @param trend   RISING / EASING / STEADY (상담 처음 vs 끝)
 * @param signals 원문이 아닌 신호 라벨 (예: '강한 불만 표현')
 * @param summary 교사용 한두 문장 + 첫 연락 팁
 */
public record ParentEmotion(String level, String trend, List<String> signals, String summary) {
}
