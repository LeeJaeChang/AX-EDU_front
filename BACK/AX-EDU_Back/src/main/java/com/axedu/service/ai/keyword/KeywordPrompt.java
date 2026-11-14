package com.axedu.service.ai.keyword;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.axedu.service.ai.openai.OpenAiChatClient.Message;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 키워드 추출 프롬프트 (src/main/resources/prompts/keyword-extraction/).
 * <ul>
 *   <li>system.md — 역할·배경·작업 순서·규칙·용어 변환 표·분류·보안 규칙</li>
 *   <li>examples.json — few-shot 예시 (user/assistant 쌍으로 넣는다)</li>
 *   <li>schema.json — Structured Outputs JSON 스키마 (필드 순서 = 생각 순서)</li>
 * </ul>
 * 문구를 고칠 때는 이 파일들만 바꾸면 된다.
 */
@Component
public class KeywordPrompt {

    static final String DIR = "prompts/keyword-extraction/";
    /** 채팅 내역은 최근 것부터 이만큼만 (토큰·개인정보 최소화) */
    static final int MAX_HISTORY_LINES = 20;
    static final int MAX_LINE_CHARS = 500;
    static final int MAX_COMPLAINT_CHARS = 3000;

    private static final Pattern PHONE = Pattern.compile("01[016789][-\\s.]?\\d{3,4}[-\\s.]?\\d{4}");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern RRN = Pattern.compile("\\d{6}[-\\s]?[1-4]\\d{6}");

    private final ObjectMapper mapper;
    private final String system;
    private final JsonNode schema;
    private final List<Message> fewShots;

    public KeywordPrompt(ObjectMapper mapper) {
        this.mapper = mapper;
        this.system = read("system.md");
        try {
            this.schema = mapper.readTree(read("schema.json"));
            List<Message> shots = new ArrayList<>();
            for (JsonNode ex : mapper.readTree(read("examples.json"))) {
                shots.add(Message.user(userMessage(ex.path("complaint").asText(), List.of())));
                shots.add(Message.assistant(mapper.writeValueAsString(ex.path("output"))));
            }
            this.fewShots = List.copyOf(shots);
        } catch (IOException e) {
            throw new UncheckedIOException("키워드 추출 프롬프트 파일을 읽지 못했습니다", e);
        }
    }

    public JsonNode schema() {
        return schema;
    }

    /** system → few-shot(user, assistant)… → 실제 민원 */
    public List<Message> messages(KeywordModels.Input input) {
        List<Message> out = new ArrayList<>(fewShots.size() + 2);
        out.add(Message.system(system));
        out.addAll(fewShots);
        out.add(Message.user(userMessage(input.complaintText(), input.history())));
        return out;
    }

    /**
     * 민원·채팅을 태그로 감싸 '자료'임을 분명히 한다 (프롬프트 인젝션 방어).
     * 연락처·이메일·주민번호는 보내기 전에 가린다.
     */
    String userMessage(String complaint, List<KeywordModels.ChatLine> history) {
        StringBuilder sb = new StringBuilder("다음 학부모 민원에서 법령 검색 키워드를 뽑아 주세요.\n\n<complaint>\n")
                .append(mask(truncate(complaint, MAX_COMPLAINT_CHARS)))
                .append("\n</complaint>");
        if (history != null && !history.isEmpty()) {
            sb.append("\n\n<chat_history>\n");
            int from = Math.max(0, history.size() - MAX_HISTORY_LINES);
            for (KeywordModels.ChatLine line : history.subList(from, history.size())) {
                if (line == null || line.text() == null || line.text().isBlank()) {
                    continue;
                }
                String who = line.speaker() == null || line.speaker().isBlank() ? "알 수 없음" : line.speaker().trim();
                sb.append('[').append(escape(who)).append("] ").append(mask(truncate(line.text(), MAX_LINE_CHARS))).append('\n');
            }
            sb.append("</chat_history>");
        }
        return sb.toString();
    }

    static String mask(String text) {
        String t = RRN.matcher(text).replaceAll("[주민번호]");
        t = PHONE.matcher(t).replaceAll("[전화번호]");
        return EMAIL.matcher(t).replaceAll("[이메일]");
    }

    /** 자료 안에서 태그를 닫아 지시처럼 보이게 하는 것을 막는다 */
    private static String escape(String s) {
        return s.replace("<", "‹").replace(">", "›");
    }

    private static String truncate(String s, int max) {
        String t = escape(s == null ? "" : s.strip());
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    private static String read(String name) {
        try (InputStream in = new ClassPathResource(DIR + name).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("프롬프트 파일이 없습니다: " + DIR + name, e);
        }
    }

    /** 테스트용: 사용자 메시지 안의 민원 부분 */
    static String complaintPart(String userMessage) {
        Matcher m = Pattern.compile("<complaint>\\n(.*)\\n</complaint>", Pattern.DOTALL).matcher(userMessage);
        return m.find() ? m.group(1) : "";
    }
}
