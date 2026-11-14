package com.axedu.service.consult;

import java.util.ArrayList;
import java.util.List;

import com.axedu.dto.chat.TimeRange;
import com.axedu.entity.enums.ComplaintTag;
import com.axedu.entity.enums.ConsultStage;

/**
 * 상담 한 건의 진행 상태 (메모리 모양).
 * stage·category·proposedTeacherId·emotionAcked 는 complaint_consult_states 의 일반 컬럼,
 * {@link Secret} 은 학부모 발화가 담겨 있어 state_enc 에 암호화해 저장한다.
 */
public class ConsultSession {

    public ConsultStage stage;
    public ComplaintTag category;
    public String proposedTeacherId;
    public boolean emotionAcked;
    public Secret secret = new Secret();

    /** 슬롯 키 (FACTS 단계에서 이 순서로 묻는다) */
    public static final List<String> ORDER = List.of("urgency", "when", "place", "detail", "impact", "request");

    /** state_enc (JSON → 암호화) */
    public static class Secret {
        public Slots slots = new Slots();
        /** 지금 답을 기다리는 질문: urgency·when·place·detail·impact·request·method·time 또는 null */
        public String pending;
        /** 카드의 '상황'을 만들 원문 (최초 민원, 자세한 이야기, 덧붙인 말) */
        public List<String> cardTexts = new ArrayList<>();
        /** 학부모가 직접 쓴 말 전체 (빠른 답 제외) — 감정 상태 판단용 */
        public List<String> parentSaid = new ArrayList<>();
    }

    public static class Slots {
        public String urgency;
        public String urgencyReason;
        public String when;
        public String place;
        /** 'given' = 자세한 이야기를 들었음 */
        public String detail;
        public String impact;
        public String request;
        public List<String> methods;
        public TimeRange timeWindow;
        public boolean timeAnswered;

        String get(String key) {
            return switch (key) {
                case "urgency" -> urgency;
                case "when" -> when;
                case "place" -> place;
                case "detail" -> detail;
                case "impact" -> impact;
                case "request" -> request;
                default -> null;
            };
        }

        void set(String key, String v) {
            switch (key) {
                case "urgency" -> urgency = v;
                case "when" -> when = v;
                case "place" -> place = v;
                case "detail" -> detail = v;
                case "impact" -> impact = v;
                case "request" -> request = v;
                default -> {
                }
            }
        }
    }

    public Slots slots() {
        return secret.slots;
    }

    /** 다음으로 비어 있는 슬롯 (없으면 null) */
    public String nextEmpty() {
        return ORDER.stream().filter(k -> secret.slots.get(k) == null).findFirst().orElse(null);
    }
}
