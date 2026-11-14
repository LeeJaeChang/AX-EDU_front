package com.axedu.service.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.axedu.entity.enums.AiRecommendation;
import com.axedu.entity.value.CardSuggestion;
import com.axedu.entity.value.SituationItem;
import com.axedu.service.ai.AiModels.CardDraft;
import com.axedu.service.ai.AiModels.CardInput;
import com.axedu.service.ai.AiModels.ExplanationInput;
import com.axedu.service.ai.AiModels.ExplanationResult;
import com.axedu.service.ai.AiModels.ReplyDraftInput;
import com.axedu.service.ai.AiModels.ReplyDraftResult;

/**
 * AI 자리표시자 (차후 작성 예정). 화면 흐름을 끝까지 시험할 수 있도록 간단한 규칙으로 결과를 만든다.
 * 원문에 "[AI실패]" 를 넣으면 첫 시도에서 실패를 재현한다 (프론트 목 서버와 같은 규칙).
 * TODO(AI): 실제 모델·규정 검색 구현으로 교체
 */
@Component
public class StubAiClient implements AiClient {

    private static final Pattern QUOTED = Pattern.compile("['‘\"“]([^'’\"”]+)['’\"”]");
    private static final Pattern REQUEST_HINT = Pattern.compile("주세요|주십시오|바랍니다|부탁|요청|원합니다");

    @Override
    public String modelName() {
        return "stub-0";
    }

    @Override
    public ExplanationResult explain(ExplanationInput input) {
        if (input.maskedText().contains("[AI실패]") && input.attempt() == 1) {
            throw new AiException("TIMEOUT", "stub failure");
        }
        return new ExplanationResult(
                "보내 주신 내용 잘 읽었어요. 많이 걱정되셨겠어요.",
                "규정 안내 기능은 준비 중이에요. 선생님께 내용을 정리해 보내 드리면 확인 후 답변을 드릴 거예요.",
                "'선생님과 대화하기'를 누르면 내용을 요약 카드로 정리해 보낼 수 있어요.",
                AiRecommendation.TALK_TO_TEACHER,
                List.of());
    }

    @Override
    public CardDraft buildCard(CardInput input) {
        List<String> sentences = Arrays.stream(input.maskedText().split("(?<=[.!?。])\\s+|\\n+"))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        List<String> requests = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (String s : sentences) {
            (REQUEST_HINT.matcher(s).find() ? requests : others).add(s);
        }
        String what = others.isEmpty() ? input.maskedText() : others.get(0);
        List<SituationItem> situation = List.of(new SituationItem(1, "", "", truncate(what, 300)));
        List<String> concerns = others.size() > 1 ? others.subList(1, Math.min(others.size(), 4)) : List.of();

        String text = input.maskedText();
        List<String> methods = new ArrayList<>();
        if (text.contains("전화")) {
            methods.add("CALL");
        }
        if (text.contains("방문") || text.contains("면담")) {
            methods.add("VISIT");
        }
        if (methods.isEmpty()) {
            methods.add("MESSAGE");
        }
        return new CardDraft(List.of("ETC"), situation, new ArrayList<>(concerns), requests, methods, null, null,
                input.citationChunkIds());
    }

    @Override
    public CardDraft revise(CardDraft base, String instruction) {
        Matcher m = QUOTED.matcher(instruction);
        if (!m.find()) {
            return null;
        }
        String quoted = m.group(1).trim();
        List<String> concerns = new ArrayList<>(base.concerns());
        List<String> requests = new ArrayList<>(base.requests());
        List<SituationItem> situation = new ArrayList<>(base.situation());
        if (instruction.contains("요청")) {
            requests.add(quoted);
        } else if (instruction.contains("걱정")) {
            concerns.add(quoted);
        } else if (instruction.contains("상황")) {
            situation.add(new SituationItem(situation.size() + 1, "", "", quoted));
        } else {
            return null;
        }
        return new CardDraft(base.tags(), situation, concerns, requests, base.preferredMethods(), base.preferredFrom(),
                base.preferredTo(), base.regulationChunkIds());
    }

    @Override
    public List<CardSuggestion> suggest(CardDraft card) {
        return List.of(); // TODO(AI): 감정 표현 순화 제안
    }

    @Override
    public ReplyDraftResult draftReply(ReplyDraftInput in) {
        String situation = blank(in.situation()) ? "" : "당시 상황을 말씀드리면, " + endSentence(in.situation());
        String reason = blank(in.reason()) ? "" : "그렇게 한 것은 " + in.reason().trim().replaceAll("[.]$", "") + "에 따른 것이었습니다.";
        String next = blank(in.nextActions()) ? "" : "앞으로는 " + endSentence(in.nextActions());
        List<String> lines = new ArrayList<>();
        lines.add("안녕하세요, " + in.studentName() + " 담임 " + in.teacherName() + "입니다.");
        lines.add("보내 주신 내용 잘 읽었습니다. 걱정을 끼쳐 드려 마음이 쓰입니다.");
        for (String s : List.of(situation, reason, next)) {
            if (!s.isEmpty()) {
                lines.add(s);
            }
        }
        lines.add("더 궁금하신 점이 있으시면 편하게 말씀해 주세요. 감사합니다.");
        return new ReplyDraftResult(String.join("\n", lines), situation, reason, next);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String endSentence(String s) {
        String t = s.trim();
        return t.matches(".*[.!?]$") ? t : t + ".";
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

}
