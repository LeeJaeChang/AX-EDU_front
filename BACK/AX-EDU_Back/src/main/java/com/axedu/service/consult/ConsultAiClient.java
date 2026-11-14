package com.axedu.service.consult;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.axedu.entity.value.ParentEmotion;
import com.axedu.service.ai.AiException;
import com.axedu.service.ai.openai.OpenAiChatClient;
import com.axedu.service.ai.openai.OpenAiChatClient.Message;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 상담에서 쓰는 ChatGPT 호출 3가지 (prompts/consult-turn, consult-explain, consult-card).
 * OPENAI_API_KEY 가 없거나 호출이 실패하면 empty 를 돌려주고, 상담은 규칙 기반으로 이어진다 (화면이 멈추지 않게).
 * 입력은 모두 가명처리된 문장이다.
 */
@Slf4j
@Component
public class ConsultAiClient {

    private final OpenAiChatClient openAi;
    private final ObjectMapper mapper;
    private final Prompt turn;
    private final Prompt explain;
    private final Prompt card;

    record Prompt(String system, JsonNode schema) {
    }

    public ConsultAiClient(OpenAiChatClient openAi, ObjectMapper mapper) {
        this.openAi = openAi;
        this.mapper = mapper;
        this.turn = load("consult-turn");
        this.explain = load("consult-explain");
        this.card = load("consult-card");
    }

    public boolean enabled() {
        return openAi.enabled();
    }

    public String model() {
        return openAi.model();
    }

    // ---------------- 1. 자유 입력 해석 ----------------

    public Optional<ConsultInterpretation> interpret(ConsultInterpretation.Request req) {
        if (!enabled()) {
            return Optional.empty();
        }
        StringBuilder user = new StringBuilder();
        user.append("<question>\n").append(req.pending() == null ? "첫 민원" : req.question()).append("\n</question>\n");
        if (req.context() != null) {
            user.append("<known>\n").append(escape(req.context())).append("\n</known>\n");
        }
        if (req.teachers() != null && !req.teachers().isEmpty()) {
            user.append("<teachers>\n");
            for (ConsultContext.Teacher t : req.teachers()) {
                user.append(t.id()).append(" | ").append(t.name()).append(" | ").append(t.homeroom() ? "담임" : "교과")
                        .append(" | ").append(String.join(", ", t.subjects()))
                        .append(t.id().equals(req.recommendedTeacherId()) ? " | 상담사 추천" : "").append('\n');
            }
            user.append("</teachers>\n");
        }
        user.append("<message>\n").append(escape(req.text())).append("\n</message>");
        return call("consult_turn", turn, user.toString(), 500, ConsultInterpretation.class);
    }

    // ---------------- 2. 규정·법령 설명 ----------------

    /** @param id 인용할 때 쓰는 ID (regulation_chunks.id 또는 법령 청크 ID) */
    public record Source(String id, String kind, String title, String text) {
    }

    public record Explanation(String empathy, String reason, String alternative, List<String> citationIds, String recommendation) {
    }

    public Optional<Explanation> explain(String consultation, String facts, List<Source> sources) {
        if (!enabled()) {
            return Optional.empty();
        }
        String src = sources.isEmpty() ? "(찾은 자료 없음)" : sources.stream()
                .map(s -> "[" + s.id() + "] " + s.kind() + " | " + s.title() + " | " + clip(s.text(), 600))
                .collect(Collectors.joining("\n"));
        String user = "<consultation>\n" + escape(consultation) + "\n</consultation>\n<facts>\n" + escape(facts)
                + "\n</facts>\n<sources>\n" + src + "\n</sources>";
        return call("consult_explain", explain, user, 900, Explanation.class);
    }

    // ---------------- 3. 요약 카드 (AI 2단계) ----------------

    public record CardSituation(String when, String where, String what) {
    }

    public record CardContent(List<String> tags, List<CardSituation> situation, List<String> concerns, List<String> requests,
                              ParentEmotion parentEmotion) {
    }

    public Optional<CardContent> writeCard(List<String> parentSaid, String facts, String regulations) {
        if (!enabled()) {
            return Optional.empty();
        }
        StringBuilder consult = new StringBuilder();
        for (int i = 0; i < parentSaid.size(); i++) {
            consult.append(i + 1).append(". ").append(parentSaid.get(i)).append('\n');
        }
        String user = "<consultation>\n" + escape(consult.toString().strip()) + "\n</consultation>\n<facts>\n" + escape(facts)
                + "\n</facts>\n<regulations>\n" + (regulations == null || regulations.isBlank() ? "(없음)" : regulations) + "\n</regulations>";
        return call("consult_card", card, user, 1500, CardContent.class);
    }

    // ---------------- 내부 ----------------

    private <T> Optional<T> call(String name, Prompt prompt, String user, int maxTokens, Class<T> type) {
        try {
            return Optional.ofNullable(openAi.structured(name, prompt.schema(),
                    List.of(Message.system(prompt.system()), Message.user(user)), maxTokens, type));
        } catch (AiException e) {
            log.warn("상담 AI({}) 실패 → 규칙으로 진행: {} {}", name, e.getErrorCode(), e.getMessage());
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("상담 AI({}) 오류 → 규칙으로 진행: {}", name, e.toString());
            return Optional.empty();
        }
    }

    /** 자료 안에서 태그를 닫아 지시처럼 보이게 하는 것을 막는다 */
    static String escape(String s) {
        return s == null ? "" : s.replace("<", "‹").replace(">", "›");
    }

    static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").strip();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    private Prompt load(String dir) {
        try {
            return new Prompt(read("prompts/" + dir + "/system.md"), mapper.readTree(read("prompts/" + dir + "/schema.json")));
        } catch (IOException e) {
            throw new UncheckedIOException("상담 프롬프트를 읽지 못했습니다: " + dir, e);
        }
    }

    private static String read(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
