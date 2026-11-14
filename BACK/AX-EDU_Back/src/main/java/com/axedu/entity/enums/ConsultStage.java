package com.axedu.entity.enums;

/**
 * AI 상담 단계 (민원 상태 EXPLAINED 안에서 진행).
 * FACTS 사실관계 묻기 → EXPLAIN 규정·법령 설명 → RECIPIENT 연락할 선생님 정하기 → CONTACT 연락 방법·시간 → READY 카드 만들 준비 완료
 */
public enum ConsultStage {
    FACTS, EXPLAIN, RECIPIENT, CONTACT, READY
}
