package com.axedu.service.ai.keyword;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.axedu.entity.enums.ComplaintTag;

/**
 * AI 가 낸 키워드를 검색에 넣기 전에 정리한다 (프롬프트 규칙을 코드로 한 번 더 보장).
 * 일반어·개인정보 흔적·중복 제거, 길이 제한, 최대 개수 제한.
 */
final class KeywordSanitizer {

    static final int MAX_KEYWORDS = 6;
    static final int MAX_TERM_CHARS = 20;
    static final int MAX_EVIDENCE_CHARS = 40;
    static final int MAX_ISSUES = 3;

    /** 거의 모든 문서에 있어 검색을 흐리는 단어 (단독으로 쓰였을 때만 뺀다) */
    static final Set<String> STOPWORDS = Set.of(
            "학교", "학생", "교사", "선생님", "담임", "담임교사", "아이", "자녀", "아들", "딸", "부모", "학부모님",
            "민원", "문제", "상황", "요청", "문의", "내용", "부분", "관련", "확인");

    /** 가림 표시(OOO)·숫자(학년·반·학번·날짜)·이메일 흔적이 있으면 개인정보로 보고 뺀다 */
    private static final Pattern MASK_OR_PII = Pattern.compile("O{2,}|○{2,}|\\d|@");
    /** 앞뒤 따옴표·괄호·기호 (법령명을 「」로 감싸는 경우가 많다) */
    private static final Pattern EDGE_JUNK = Pattern.compile(
            "^[\\s\"'“”‘’.,·:;!?()\\[\\]{}#*「」『』《》〈〉<>‹›-]+|[\\s\"'“”‘’.,·:;!?()\\[\\]{}#*「」『』《》〈〉<>‹›-]+$");

    private KeywordSanitizer() {
    }

    static List<KeywordModels.Keyword> keywords(List<KeywordModels.Keyword> raw) {
        List<KeywordModels.Keyword> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (raw == null) {
            return out;
        }
        for (KeywordModels.Keyword k : raw) {
            if (k == null || k.term() == null) {
                continue;
            }
            String term = EDGE_JUNK.matcher(k.term().strip()).replaceAll("").replaceAll("\\s+", " ");
            if (term.length() < 2 || term.length() > MAX_TERM_CHARS || STOPWORDS.contains(term) || MASK_OR_PII.matcher(term).find()) {
                continue;
            }
            if (!seen.add(term.replace(" ", "").toLowerCase(Locale.ROOT))) {
                continue;
            }
            out.add(new KeywordModels.Keyword(term, cut(k.evidence(), MAX_EVIDENCE_CHARS)));
            if (out.size() >= MAX_KEYWORDS) {
                break;
            }
        }
        return out;
    }

    static List<String> issues(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream().filter(s -> s != null && !s.isBlank()).map(String::strip).limit(MAX_ISSUES).toList();
    }

    static ComplaintTag category(String raw) {
        if (raw == null) {
            return ComplaintTag.ETC;
        }
        try {
            return ComplaintTag.valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ComplaintTag.ETC;
        }
    }

    static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.strip();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
