package com.axedu.service.ai.keyword;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.axedu.entity.enums.ComplaintTag;
import com.axedu.service.ai.AiException;

/**
 * OpenAI 키가 없을 때 쓰는 대체 구현. 프롬프트의 '용어 변환 표'와 같은 규칙으로 구어 표현을 법령 용어로 바꾼다.
 * 로컬 개발·테스트에서 AI 없이 흐름을 확인하기 위한 것이라 정확도는 낮다.
 */
public class RuleBasedKeywordExtractor implements KeywordExtractor {

    public static final String MODEL = "rule-based";

    /** 구어 패턴 → 키워드 (순서 = 중요도) */
    private static final Map<Pattern, String> RULES = new LinkedHashMap<>();
    /** 키워드 → 분류 */
    private static final Map<String, ComplaintTag> CATEGORY = new LinkedHashMap<>();

    static {
        rule("폰|핸드폰|휴대폰|휴대전화|스마트폰", "휴대전화", ComplaintTag.LIFE_GUIDANCE);
        rule("뺏|빼앗|압수|걷어|안\\s*돌려", "압수", ComplaintTag.LIFE_GUIDANCE);
        rule("가방.{0,4}(뒤지|검사)|소지품", "소지품 검사", ComplaintTag.LIFE_GUIDANCE);
        rule("때리|때렸|손찌검|꿀밤|체벌", "체벌", ComplaintTag.LIFE_GUIDANCE);
        rule("소리\\s*(지르|질러)|막말|폭언|모욕|욕(을|설|하)", "폭언", ComplaintTag.ETC);
        rule("왕따|따돌|따시키|끼워\\s*주지", "따돌림", ComplaintTag.PEER_RELATION);
        rule("괴롭|단톡|학교\\s*폭력|학폭|놀림", "학교폭력", ComplaintTag.PEER_RELATION);
        rule("가해", "가해학생", ComplaintTag.PEER_RELATION);
        rule("반성문|벌\\s*세|남겨서|훈육|생활\\s*지도", "학생생활지도", ComplaintTag.LIFE_GUIDANCE);
        rule("벌점|상점", "벌점", ComplaintTag.LIFE_GUIDANCE);
        rule("두발|머리\\s*(길이|색)|염색", "두발", ComplaintTag.LIFE_GUIDANCE);
        rule("복장|교복|화장", "복장", ComplaintTag.LIFE_GUIDANCE);
        rule("수행\\s*평가", "수행평가", ComplaintTag.STUDY_EVALUATION);
        rule("점수|성적|채점|시험", "성적", ComplaintTag.STUDY_EVALUATION);
        rule("생기부|생활\\s*기록부", "학교생활기록", ComplaintTag.STUDY_EVALUATION);
        rule("담임.{0,6}(바꿔|교체|바꾸)", "담임 교체", ComplaintTag.ETC);
        rule("아동\\s*학대|학대", "아동학대", ComplaintTag.ETC);
        rule("신고|고소|고발", "신고", ComplaintTag.ETC);
        rule("녹음", "녹음", ComplaintTag.ETC);
        rule("다쳤|다치|부상|안전\\s*사고", "안전사고", ComplaintTag.SAFETY);
        rule("급식|알레르기", "급식", ComplaintTag.SAFETY);
        rule("교실\\s*밖|내보냈|따로\\s*앉", "분리", ComplaintTag.LIFE_GUIDANCE);
        rule("결석|지각|출석|출결", "출결", ComplaintTag.STUDY_EVALUATION);
        rule("공개|다른\\s*(애|학생)들?\\s*앞", "개인정보", ComplaintTag.ETC);
    }

    private static void rule(String regex, String term, ComplaintTag tag) {
        RULES.put(Pattern.compile(regex), term);
        CATEGORY.putIfAbsent(term, tag);
    }

    @Override
    public String modelName() {
        return MODEL;
    }

    @Override
    public KeywordModels.Result extract(KeywordModels.Input input) {
        if (input == null || input.complaintText() == null || input.complaintText().isBlank()) {
            throw new AiException("EMPTY_INPUT", "민원 내용이 비어 있습니다.");
        }
        StringBuilder all = new StringBuilder(input.complaintText());
        if (input.history() != null) {
            input.history().stream().filter(l -> l != null && l.text() != null).forEach(l -> all.append('\n').append(l.text()));
        }
        String text = all.toString();
        List<KeywordModels.Keyword> found = new ArrayList<>();
        for (Map.Entry<Pattern, String> r : RULES.entrySet()) {
            Matcher m = r.getKey().matcher(text);
            if (m.find()) {
                found.add(new KeywordModels.Keyword(r.getValue(), around(text, m.start(), m.end())));
            }
        }
        List<KeywordModels.Keyword> keywords = KeywordSanitizer.keywords(found);
        ComplaintTag category = keywords.stream()
                .map(k -> CATEGORY.get(k.term()))
                .filter(t -> t != null && t != ComplaintTag.ETC)
                .findFirst().orElse(ComplaintTag.ETC);
        String first = input.complaintText().strip().split("(?<=[.!?。])\\s|\\n")[0];
        return new KeywordModels.Result(KeywordSanitizer.cut(first, 60), List.of(), keywords, category, MODEL);
    }

    private static String around(String text, int start, int end) {
        int s = Math.max(0, start - 6);
        int e = Math.min(text.length(), end + 8);
        return text.substring(s, e).replaceAll("\\s+", " ").strip();
    }
}
