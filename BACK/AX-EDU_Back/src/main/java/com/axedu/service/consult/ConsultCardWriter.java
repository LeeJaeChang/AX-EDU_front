package com.axedu.service.consult;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.axedu.dto.chat.TimeRange;
import com.axedu.entity.Complaint;
import com.axedu.entity.enums.ComplaintTag;
import com.axedu.entity.enums.UrgencyLevel;
import com.axedu.entity.value.ParentEmotion;
import com.axedu.entity.value.SituationItem;
import com.axedu.service.ComplaintMasker;

import lombok.RequiredArgsConstructor;

/**
 * 상담 전체 → 요약 카드 (AI 2단계).
 *
 * <pre>
 * 학부모가 상담에서 쓴 말 + 정리된 사실관계 + 규정·법령 설명 → (ChatGPT) 카드 JSON(상황·걱정·요청·유형·감정 상태)
 * → 서버가 상담에서 확인한 값(일시·장소·대응 시급도·희망 연락)을 덧입히고, 책임 추궁·외부 신고 문장은 뺀다
 * </pre>
 *
 * AI 가 꺼져 있거나 실패하면 프론트 목(buildCard + cardFromConsult)과 같은 규칙으로 만든다.
 */
@Component
@RequiredArgsConstructor
public class ConsultCardWriter {

    private final ConsultAiClient ai;
    private final ComplaintMasker masker;

    /** 카드 한 버전의 내용 (기본 정보·버전 제외) */
    public record Card(List<String> tags, List<SituationItem> situation, List<String> concerns, List<String> requests,
                       List<String> methods, LocalTime preferredFrom, LocalTime preferredTo,
                       UrgencyLevel urgency, String urgencyReason, ParentEmotion parentEmotion, String model) {
    }

    public Card write(Complaint c, ConsultSession cs, String facts, String regulationSummary) {
        Card base = Optional.ofNullable(fromAi(c, cs, facts, regulationSummary)).orElseGet(() -> fromRules(cs.secret.cardTexts, cs.category));
        return overlay(base, cs);
    }

    // ---------------- AI ----------------

    private Card fromAi(Complaint c, ConsultSession cs, String facts, String regulationSummary) {
        List<String> said = cs.secret.parentSaid.isEmpty() ? cs.secret.cardTexts : cs.secret.parentSaid;
        Optional<ConsultAiClient.CardContent> out = ai.writeCard(masker.mask(c, said), facts, regulationSummary);
        if (out.isEmpty() || out.get().situation() == null || out.get().situation().isEmpty()) {
            return null;
        }
        ConsultAiClient.CardContent x = out.get();
        List<SituationItem> situation = new ArrayList<>();
        for (ConsultAiClient.CardSituation s : x.situation()) {
            String what = clean(s.what());
            if (what.length() >= 4 && !ConsultText.THREAT.matcher(s.what()).find()) {
                situation.add(new SituationItem(situation.size() + 1, dash(s.when()), dash(s.where()), what));
            }
            if (situation.size() >= 8) {
                break;
            }
        }
        if (situation.isEmpty()) {
            return null;
        }
        List<String> tags = (x.tags() == null ? List.<String>of() : x.tags()).stream()
                .filter(t -> Arrays.stream(ComplaintTag.values()).anyMatch(e -> e.name().equals(t))).distinct().limit(3).toList();
        return new Card(tags.isEmpty() ? ConsultRules.tagsOf(ConsultRules.rulesOf(cs.secret.cardTexts, cs.category)) : tags,
                situation, cleanList(x.concerns(), 5), cleanList(x.requests(), 6), null, null, null, null, null,
                mergeEmotion(ConsultText.assessEmotion(cs.secret.parentSaid), x.parentEmotion()), ai.model());
    }

    /** 규칙 판단을 바닥으로 두고 AI 판단을 더한다 (책임 추궁·외부 신고 언급은 항상 '격양됨') */
    static ParentEmotion mergeEmotion(ParentEmotion rule, ParentEmotion fromAi) {
        if (fromAi == null) {
            return rule;
        }
        List<String> levels = List.of("CALM", "CONCERNED", "AGITATED");
        String level = levels.indexOf(fromAi.level()) > levels.indexOf(rule.level()) ? fromAi.level() : rule.level();
        Set<String> signals = new LinkedHashSet<>(rule.signals());
        if (fromAi.signals() != null) {
            fromAi.signals().stream().filter(ConsultText.SIGNAL_LABELS::contains).forEach(signals::add);
        }
        List<String> ordered = ConsultText.SIGNAL_LABELS.stream().filter(signals::contains).toList();
        // 흐름은 명세 정의(첫 메시지 vs 마지막 메시지 점수)대로 규칙으로 정한다
        String trend = rule.trend();
        String summary = fromAi.summary() != null && !fromAi.summary().isBlank() && level.equals(fromAi.level()) && trend.equals(fromAi.trend())
                ? clip(fromAi.summary(), 300) : ConsultText.emotionSummary(level, trend);
        return new ParentEmotion(level, trend, ordered, summary);
    }

    // ---------------- 규칙 (프론트 목 buildCard) ----------------

    static Card fromRules(List<String> texts, ComplaintTag category) {
        String text = String.join("\n", texts);
        List<ConsultRules.Rule> rules = ConsultRules.rulesOf(texts, category);
        List<SituationItem> situation = new ArrayList<>();
        List<String> concerns = new ArrayList<>();
        List<String> requests = new ArrayList<>();
        for (String s : texts.stream().flatMap(t -> ConsultText.sentencesOf(t).stream()).toList()) {
            String clean = ConsultText.soften(s);
            if (clean.length() < 4) {
                continue; // 감정 표현만 있던 문장은 버린다
            }
            if (ConsultText.THREAT.matcher(s).find()) {
                continue; // 신고·책임 추궁 표현은 마음 상태 신호로만
            }
            if (ConsultText.CONTACT_PREF.matcher(s).find() && (ConsultText.parseMethods(s) != null || ConsultText.parseTimeWindow(s) != null)) {
                continue;
            }
            if (ConsultText.REQUEST.matcher(s).find()) {
                requests.add(clean);
            } else if (ConsultText.CONCERN.matcher(s).find()) {
                concerns.add(clean);
            } else {
                situation.add(new SituationItem(situation.size() + 1, ConsultText.cardWhen(s), ConsultText.cardWhere(s), clean));
            }
        }
        if (situation.isEmpty()) {
            for (ConsultRules.Rule r : rules) {
                for (SituationItem x : r.situation()) {
                    situation.add(new SituationItem(situation.size() + 1, x.when(), x.where(), x.what()));
                }
            }
        }
        rules.stream().flatMap(r -> r.concerns().stream()).filter(x -> !concerns.contains(x)).forEach(concerns::add);
        rules.stream().flatMap(r -> r.requests().stream()).filter(x -> !requests.contains(x)).forEach(requests::add);

        // 연락 방법은 마지막으로 말한 것을 따른다
        List<String> reversed = new ArrayList<>(texts);
        java.util.Collections.reverse(reversed);
        String contactText = reversed.stream().filter(t -> ConsultText.parseMethods(t) != null || ConsultText.parseTimeWindow(t) != null)
                .findFirst().orElse(text);
        List<String> methods = Optional.ofNullable(ConsultText.parseMethods(contactText)).orElse(List.of("MESSAGE"));
        TimeRange tw = ConsultText.parseTimeWindow(contactText);
        UrgencyLevel urgency = Optional.ofNullable(ConsultText.detectUrgency(text)).orElse(UrgencyLevel.NORMAL);
        return new Card(ConsultRules.tagsOf(rules), situation.subList(0, Math.min(8, situation.size())),
                new ArrayList<>(concerns.subList(0, Math.min(5, concerns.size()))),
                new ArrayList<>(requests.subList(0, Math.min(6, requests.size()))),
                methods, tw == null ? null : LocalTime.parse(tw.from()), tw == null ? null : LocalTime.parse(tw.to()),
                urgency, ConsultText.urgencyReason(text), ConsultText.assessEmotion(texts), RuleBasedModel.NAME);
    }

    // ---------------- 상담에서 확인한 값 덧입히기 (프론트 목 cardFromConsult) ----------------

    static Card overlay(Card base, ConsultSession cs) {
        ConsultSession.Slots s = cs.slots();
        List<String> wants = s.request == null ? List.of()
                : Arrays.stream(s.request.split(" / ")).map(String::trim).filter(w -> !w.isEmpty()).toList();
        Set<String> moved = new LinkedHashSet<>();
        if (s.impact != null) {
            moved.add(s.impact);
        }
        moved.addAll(wants);

        // 아이 상태·바라는 점으로 옮긴 문장은 상황에서 빼고, 첫 상황의 일시·장소는 상담에서 확인한 값으로
        List<SituationItem> situation = new ArrayList<>();
        for (SituationItem x : base.situation()) {
            if (moved.contains(x.what())) {
                continue;
            }
            boolean first = situation.isEmpty();
            situation.add(new SituationItem(situation.size() + 1,
                    first && known(s.when) ? s.when : x.when(),
                    first && known(s.place) ? s.place : x.where(), x.what()));
        }
        if (situation.isEmpty()) {
            situation.addAll(base.situation());
        }
        // 상담에서 확인한 아이 상태·바라는 점을 맨 앞에. 같은 뜻을 다시 쓴 항목은 뺀다
        List<String> concerns = new ArrayList<>(base.concerns());
        if (s.impact != null && !s.impact.contains("괜찮")) {
            concerns.removeIf(x -> overlaps(x, s.impact));
            concerns.add(0, s.impact);
        }
        List<String> requests = new ArrayList<>(base.requests().stream()
                .filter(r -> wants.stream().noneMatch(w -> overlaps(r, w))).toList());
        requests.addAll(0, wants);

        List<String> methods = s.methods != null && !s.methods.isEmpty() ? s.methods
                : base.methods() != null ? base.methods() : List.of("MESSAGE");
        LocalTime from = base.preferredFrom();
        LocalTime to = base.preferredTo();
        if (s.timeAnswered) {
            from = s.timeWindow == null ? null : LocalTime.parse(s.timeWindow.from());
            to = s.timeWindow == null ? null : LocalTime.parse(s.timeWindow.to());
        }
        UrgencyLevel urgency = s.urgency != null ? UrgencyLevel.valueOf(s.urgency)
                : base.urgency() != null ? base.urgency() : UrgencyLevel.NORMAL;
        String reason = s.urgency != null ? s.urgencyReason : base.urgencyReason();
        if (urgency != UrgencyLevel.URGENT) {
            reason = null;
        }
        // 상담 전체에서 학부모가 직접 쓴 말로 판단 (빠른 답 제외)
        ParentEmotion emotion = base.parentEmotion() != null && !RuleBasedModel.NAME.equals(base.model())
                ? base.parentEmotion() : ConsultText.assessEmotion(cs.secret.parentSaid);
        return new Card(base.tags(), situation, dedupe(concerns, 5), dedupe(requests, 6), methods, from, to, urgency, reason, emotion,
                base.model());
    }

    /** 앞에 있는 항목과 거의 같은 말(글자 2개씩 겹침 60% 이상)은 뺀다 */
    static boolean similar(String a, String b) {
        return overlap(a, b) >= 0.6;
    }

    /** 상담에서 확인한 문장(아이 상태·바라는 점)과 겹치는 AI 문장 — 기준을 낮춰(35%) 같은 뜻의 되풀이를 뺀다 */
    static boolean overlaps(String a, String b) {
        return overlap(a, b) >= 0.35;
    }

    static double overlap(String a, String b) {
        Set<String> x = bigrams(a);
        Set<String> y = bigrams(b);
        if (x.isEmpty() || y.isEmpty()) {
            return a.equals(b) ? 1 : 0;
        }
        Set<String> inter = new LinkedHashSet<>(x);
        inter.retainAll(y);
        return (double) inter.size() / Math.min(x.size(), y.size());
    }

    private static Set<String> bigrams(String s) {
        String t = s.replaceAll("[^가-힣a-zA-Z0-9]", "");
        Set<String> out = new LinkedHashSet<>();
        for (int i = 0; i + 1 < t.length(); i++) {
            out.add(t.substring(i, i + 2));
        }
        return out;
    }

    private static boolean known(String v) {
        return v != null && !v.equals("확인 필요") && !v.equals("given");
    }

    private static List<String> dedupe(List<String> in, int max) {
        List<String> out = new ArrayList<>();
        for (String x : in) {
            if (out.stream().noneMatch(o -> similar(o, x))) {
                out.add(x);
            }
            if (out.size() >= max) {
                break;
            }
        }
        return out;
    }

    private static List<String> cleanList(List<String> in, int max) {
        if (in == null) {
            return new ArrayList<>();
        }
        return in.stream().filter(x -> x != null && !ConsultText.THREAT.matcher(x).find()).map(ConsultCardWriter::clean)
                .filter(x -> x.length() >= 2).distinct().limit(max).collect(Collectors.toCollection(ArrayList::new));
    }

    private static String clean(String s) {
        return clip(ConsultText.soften(s == null ? "" : s), 200);
    }

    private static String dash(String s) {
        return s == null || s.isBlank() ? "—" : clip(s.trim(), 30);
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** 규칙으로 만든 카드의 모델 이름 */
    static final class RuleBasedModel {
        static final String NAME = "rule-based";

        private RuleBasedModel() {
        }
    }
}
