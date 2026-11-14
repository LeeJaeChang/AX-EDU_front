package com.axedu.service.consult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.axedu.dto.chat.TimeRange;
import com.axedu.entity.enums.UrgencyLevel;
import com.axedu.entity.value.ParentEmotion;

/**
 * 상담·카드에 쓰는 문장 규칙 (프론트 목 mockAi.ts 와 같은 규칙).
 * AI(LLM)가 없을 때의 대체 구현이자, AI 결과를 한 번 더 거르는 안전장치로 쓴다.
 */
public final class ConsultText {

    private ConsultText() {
    }

    // ---------------- 감정 표현 순화 ----------------

    static final Pattern EMOTION = Pattern.compile(
            "(이게 )?말이 (됩니까|되나요|돼요|되냐)|이해가 안 ?(됩니다|돼요|가요|갑니다)|어이(가)? ?없|황당|화가 나|기가 막|도대체|어떻게 이럴|너무하|최악|무책임|제정신|가만 안|[!?]{2,}|ㅡㅡ|;;|ㅠ+|ㅜ+");

    public static boolean hasEmotion(String s) {
        return s != null && EMOTION.matcher(s).find();
    }

    /** 감정 표현·느낌표를 덜어 낸 문장 */
    public static String soften(String s) {
        if (s == null) {
            return "";
        }
        return EMOTION.matcher(s).replaceAll("")
                .replaceAll("[!?]+", "")
                .replaceAll("\\s{2,}", " ")
                .replaceAll("\\s+([.,])", "$1")
                .trim();
    }

    // ---------------- 대응 시급도 ----------------

    /** 안전·건강이 걸린 표현 → 긴급 후보 */
    static final Pattern SAFETY_URGENT = Pattern.compile("다쳤|다치|부상|병원|응급|피가|폭력|때렸|맞았|자해|죽고 싶|위험|협박|성희롱|성추행");
    static final Pattern TIME_URGENT = Pattern.compile("오늘 안에|오늘 중|당장|바로|급해|급합니다|급한");

    public static UrgencyLevel detectUrgency(String text) {
        if (text == null) {
            return null;
        }
        if (SAFETY_URGENT.matcher(text).find() || TIME_URGENT.matcher(text).find()) {
            return UrgencyLevel.URGENT;
        }
        if (Pattern.compile("며칠|이번 ?주|내일").matcher(text).find()) {
            return UrgencyLevel.SOON;
        }
        return null;
    }

    public static String urgencyReason(String text) {
        if (text == null) {
            return null;
        }
        if (SAFETY_URGENT.matcher(text).find()) {
            return "학생 안전·건강 관련";
        }
        if (TIME_URGENT.matcher(text).find()) {
            return "보호자가 빠른 확인 요청";
        }
        return null;
    }

    public static boolean mentionsSafety(String text) {
        return text != null && SAFETY_URGENT.matcher(text).find();
    }

    // ---------------- 학부모 감정 상태 ----------------

    /** 책임 추궁·외부 신고 표현 — 카드 본문에는 넣지 않고 '학부모 마음 상태' 신호로만 전한다 */
    public static final Pattern THREAT = Pattern.compile("무책임|제정신|가만 안|고소|교육청|신고|민원 넣|언론|책임지");

    record Signal(String label, Pattern re, int weight) {
    }

    /** 신호 라벨 (LLM 도 이 목록 안에서만 고른다) */
    public static final List<String> SIGNAL_LABELS = List.of(
            "강한 불만 표현", "반복된 느낌표·물음표", "책임 추궁·외부 신고 언급", "불안·걱정 표현", "아이가 힘들어한다는 표현");

    static final List<Signal> SIGNALS = List.of(
            new Signal(SIGNAL_LABELS.get(0), Pattern.compile(
                    "말이 (됩니까|되나요|돼요|되냐)|어이(가)? ?없|황당|화가 나|기가 막|너무하|최악|이해가 안 ?(됩니다|돼요|가요|갑니다)|도대체|어떻게 이럴"), 2),
            new Signal(SIGNAL_LABELS.get(1), Pattern.compile("[!?]{2,}"), 1),
            new Signal(SIGNAL_LABELS.get(2), THREAT, 3),
            new Signal(SIGNAL_LABELS.get(3), Pattern.compile("걱정|불안|무서|잠을 못|어떡"), 0),
            new Signal(SIGNAL_LABELS.get(4), Pattern.compile("속상|울었|울어|힘들어|싫다고|시무룩|당황"), 0));

    static int scoreOf(String t) {
        int n = 0;
        for (Signal s : SIGNALS) {
            if (s.re().matcher(t).find()) {
                n += s.weight();
            }
        }
        return n;
    }

    /**
     * 학부모가 직접 쓴 말 전체로 감정 상태를 정리한다. 원문은 드러내지 않고 신호 분류만 남긴다.
     * 격양: 한 메시지 점수 3 이상 또는 책임 추궁·외부 신고 언급 / 걱정·속상함: 그 외 감정 신호 / 차분함: 신호 없음
     */
    public static ParentEmotion assessEmotion(List<String> texts) {
        List<String> said = texts == null ? List.of() : texts.stream().filter(t -> t != null && !t.isBlank()).toList();
        List<Integer> scores = said.stream().map(ConsultText::scoreOf).toList();
        List<String> signals = SIGNALS.stream().filter(s -> said.stream().anyMatch(t -> s.re().matcher(t).find()))
                .map(Signal::label).toList();
        int peak = scores.stream().mapToInt(Integer::intValue).max().orElse(0);
        String level = peak >= 3 || signals.contains(SIGNAL_LABELS.get(2)) ? "AGITATED" : signals.isEmpty() ? "CALM" : "CONCERNED";
        int first = scores.isEmpty() ? 0 : scores.get(0);
        int last = scores.isEmpty() ? 0 : scores.get(scores.size() - 1);
        String trend = said.size() < 2 ? "STEADY" : last < first ? "EASING" : last > first ? "RISING" : "STEADY";
        return new ParentEmotion(level, trend, signals, emotionSummary(level, trend));
    }

    public static String emotionSummary(String level, String trend) {
        String base = switch (level) {
            case "AGITATED" -> "상담 중 강한 불만과 답답함을 표현하셨어요.";
            case "CONCERNED" -> "아이를 걱정하고 속상해하는 마음이 크세요.";
            default -> "차분하게 상황을 설명해 주셨어요.";
        };
        String flow = switch (trend) {
            case "EASING" -> " 대화를 이어 가며 조금씩 차분해지셨어요.";
            case "RISING" -> " 대화가 이어질수록 감정이 더 격해지셨어요.";
            default -> "";
        };
        String tip = switch (level) {
            case "AGITATED" -> " 첫 연락은 먼저 마음을 알아주는 말로 시작하고, 사실관계는 그다음에 차분히 설명하시길 권해요.";
            case "CONCERNED" -> " 아이가 어떻게 지내는지부터 함께 이야기하면 좋아요.";
            default -> "";
        };
        return base + flow + tip;
    }

    // ---------------- 문장·시간·연락 방법 ----------------

    public static final Pattern REQUEST = Pattern.compile("(주세요|주시면|주셨으면|바랍니다|원합니다|부탁|요청)");
    public static final Pattern CONCERN = Pattern.compile("(걱정|불안|힘들어|속상|무서워|울었|잠을 못)");
    static final Pattern CARD_WHEN = Pattern.compile("(오늘|어제|그저께|그제|지난주|이번 주|\\d{1,2}월 ?\\d{1,2}일|\\d교시|수업 ?중|점심시간|쉬는 ?시간|아침|종례|조회|방과 ?후|하교|등교)");
    static final Pattern CARD_WHERE = Pattern.compile("(교실|운동장|급식실|체육관|복도|화장실|도서관|과학실|음악실|교무실|교문|학원)");
    /** 연락 방법·시간 문장은 '희망 연락'으로 옮기고 상황·요청에서는 뺀다 */
    static final Pattern CONTACT_PREF = Pattern.compile("(연락은|연락 ?주|전화로|통화로|문자로|방문해서|면담으로)");

    public static List<String> sentencesOf(String text) {
        if (text == null) {
            return List.of();
        }
        return Arrays.stream(text.split("(?<=[.!?…])\\s+|\\n+"))
                .map(String::trim)
                .filter(s -> s.length() > 1)
                .toList();
    }

    private static final Pattern TIME_WINDOW = Pattern.compile(
            "(오전|오후)?\\s*(\\d{1,2})(?::(\\d{2}))?\\s*시?\\s*(?:~|-|부터|에서)\\s*(오전|오후)?\\s*(\\d{1,2})(?::(\\d{2}))?\\s*시?");

    /** '오후 4~5시', '16:00-17:00' → {16:00, 17:00} */
    public static TimeRange parseTimeWindow(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = TIME_WINDOW.matcher(text);
        if (!m.find()) {
            return null;
        }
        boolean pm = "오후".equals(m.group(1));
        int fromH = to24(m.group(1), m.group(2), false);
        int toH = to24(m.group(4) != null ? m.group(4) : (pm ? "오후" : null), m.group(5), pm);
        if (fromH >= 24 || toH > 24 || fromH >= toH) {
            return null;
        }
        return new TimeRange(pad(fromH) + ":" + (m.group(3) == null ? "00" : m.group(3)),
                pad(toH) + ":" + (m.group(6) == null ? "00" : m.group(6)));
    }

    private static int to24(String ampm, String h, boolean fallbackPm) {
        int hh = Integer.parseInt(h);
        if (("오후".equals(ampm) || (ampm == null && fallbackPm)) && hh < 12) {
            hh += 12;
        }
        return hh;
    }

    private static String pad(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }

    /** 연락 방법 (CALL·VISIT·MESSAGE). 없으면 null */
    public static List<String> parseMethods(String text) {
        if (text == null) {
            return null;
        }
        List<String> out = new ArrayList<>();
        if (Pattern.compile("전화|통화").matcher(text).find()) {
            out.add("CALL");
        }
        if (Pattern.compile("방문|찾아뵙|면담|직접 만나").matcher(text).find()) {
            out.add("VISIT");
        }
        if (Pattern.compile("문자|메시지|채팅|글로").matcher(text).find()) {
            out.add("MESSAGE");
        }
        return out.isEmpty() ? null : out;
    }

    public static String cardWhen(String s) {
        Matcher m = CARD_WHEN.matcher(s);
        return m.find() ? m.group(1) : "—";
    }

    public static String cardWhere(String s) {
        Matcher m = CARD_WHERE.matcher(s);
        return m.find() ? m.group(1) : "—";
    }

    /** 받침에 따라 '으로' / '로' (ㄹ 받침은 '로') */
    public static String euro(String word) {
        if (word == null || word.isEmpty()) {
            return "(으)로";
        }
        int code = word.charAt(word.length() - 1) - 0xAC00;
        if (code < 0 || code > 11171) {
            return "(으)로";
        }
        int jong = code % 28;
        return jong == 0 || jong == 8 ? "로" : "으로";
    }
}
