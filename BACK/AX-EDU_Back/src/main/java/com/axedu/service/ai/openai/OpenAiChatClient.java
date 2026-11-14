package com.axedu.service.ai.openai;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.axedu.config.AppProperties;
import com.axedu.service.ai.AiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

/**
 * OpenAI Chat Completions + Structured Outputs(JSON 스키마 강제) 호출.
 * 응답이 항상 스키마에 맞는 JSON 이라 파싱이 깨지지 않는다. 키워드 추출·요약 카드 생성 등 AI 단계가 함께 쓴다.
 *
 * <p>민원 원문 같은 개인정보가 담길 수 있으므로 요청·응답 본문은 로그에 남기지 않는다.
 */
@Slf4j
@Component
public class OpenAiChatClient {

    /** temperature 를 받지 않는 추론 모델 (gpt-5 계열, o1·o3·o4 계열) */
    private static final Pattern NO_TEMPERATURE_MODEL = Pattern.compile("^(gpt-5|o\\d).*");

    private final RestClient rest;
    private final ObjectMapper mapper;
    private final AppProperties.OpenAi cfg;

    public OpenAiChatClient(RestClient.Builder builder, AppProperties props, ObjectMapper mapper) {
        this.cfg = props.openai();
        this.mapper = mapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.min(cfg.timeoutMillis(), 10_000)));
        factory.setReadTimeout(Duration.ofMillis(cfg.timeoutMillis()));
        this.rest = builder.clone()
                .baseUrl(cfg.baseUrl())
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + (cfg.apiKey() == null ? "" : cfg.apiKey().trim()))
                .build();
    }

    public String model() {
        return cfg.model();
    }

    public boolean enabled() {
        return cfg.enabled();
    }

    /** 대화 메시지 (role: system / user / assistant) */
    public record Message(String role, String content) {

        public static Message system(String content) {
            return new Message("system", content);
        }

        public static Message user(String content) {
            return new Message("user", content);
        }

        public static Message assistant(String content) {
            return new Message("assistant", content);
        }
    }

    /**
     * 스키마에 맞는 JSON 을 받아 type 으로 바꾼다.
     *
     * @param schemaName 스키마 이름 (영문·숫자·_·-)
     * @param schema     JSON 스키마 (strict 모드: 모든 필드 required, additionalProperties false)
     * @throws AiException 키 오류·시간 초과·거절·형식 오류 등 (errorCode 로 구분)
     */
    public <T> T structured(String schemaName, JsonNode schema, List<Message> messages, int maxTokens, Class<T> type) {
        if (!cfg.enabled()) {
            throw new AiException("NOT_CONFIGURED", "OpenAI API 키가 설정되지 않았습니다 (.env 의 OPENAI_API_KEY).");
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("model", cfg.model());
        ArrayNode msgs = body.putArray("messages");
        for (Message m : messages) {
            msgs.addObject().put("role", m.role()).put("content", m.content());
        }
        ObjectNode format = body.putObject("response_format");
        format.put("type", "json_schema");
        ObjectNode js = format.putObject("json_schema");
        js.put("name", schemaName);
        js.put("strict", true);
        js.set("schema", schema);
        body.put("max_completion_tokens", maxTokens);
        if (cfg.temperature() != null && !NO_TEMPERATURE_MODEL.matcher(cfg.model()).matches()) {
            body.put("temperature", cfg.temperature());
        }

        long started = System.currentTimeMillis();
        JsonNode res;
        try {
            res = rest.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpStatusCodeException e) {
            throw httpError(e.getStatusCode(), errorMessage(e));
        } catch (ResourceAccessException e) {
            log.warn("OpenAI 응답 없음 ({}ms): {}", System.currentTimeMillis() - started, e.getMessage());
            throw new AiException("TIMEOUT", "AI 응답이 늦어지고 있어요. 잠시 후 다시 시도해 주세요.");
        } catch (RestClientException e) {
            log.warn("OpenAI 호출 실패: {}", e.getMessage());
            throw new AiException("UPSTREAM_ERROR", "AI 서버 호출에 실패했습니다.");
        }
        log.debug("OpenAI {} 응답 {}ms, usage={}", cfg.model(), System.currentTimeMillis() - started, res == null ? null : res.path("usage"));
        return parse(res, type);
    }

    private <T> T parse(JsonNode res, Class<T> type) {
        JsonNode choice = res == null ? null : res.path("choices").path(0);
        if (choice == null || choice.isMissingNode()) {
            throw new AiException("INVALID_RESPONSE", "AI 응답 형식이 올바르지 않습니다.");
        }
        JsonNode message = choice.path("message");
        if (message.hasNonNull("refusal") && !message.path("refusal").asText().isBlank()) {
            throw new AiException("REFUSED", "AI 가 이 요청을 처리하지 않았습니다.");
        }
        if ("length".equals(choice.path("finish_reason").asText())) {
            throw new AiException("TRUNCATED", "AI 응답이 너무 길어 잘렸습니다.");
        }
        String content = message.path("content").asText("");
        try {
            return mapper.readValue(content, type);
        } catch (JsonProcessingException e) {
            throw new AiException("INVALID_RESPONSE", "AI 응답을 해석하지 못했습니다.");
        }
    }

    private static AiException httpError(HttpStatusCode status, String detail) {
        int code = status.value();
        // 401·403 오류 문구에는 키 일부가 들어갈 수 있어 남기지 않는다
        log.warn("OpenAI HTTP {}: {}", code, code == 401 || code == 403 ? "(인증 실패)" : detail);
        return switch (code) {
            case 401, 403 -> new AiException("UNAUTHORIZED", "OpenAI API 키를 확인해 주세요 (.env 의 OPENAI_API_KEY).");
            case 404 -> new AiException("MODEL_NOT_FOUND", "OpenAI 모델을 찾을 수 없습니다 (AXEDU_OPENAI_MODEL 확인): " + detail);
            case 429 -> new AiException("RATE_LIMITED", "AI 요청이 많아 잠시 후 다시 시도해 주세요.");
            case 400 -> new AiException("BAD_REQUEST", "AI 요청이 거절되었습니다: " + detail);
            default -> new AiException("UPSTREAM_ERROR", "AI 서버 오류 (" + code + ")");
        };
    }

    private String errorMessage(HttpStatusCodeException e) {
        try {
            JsonNode n = mapper.readTree(e.getResponseBodyAsString());
            String msg = n.path("error").path("message").asText("");
            return msg.isBlank() ? e.getStatusText() : msg;
        } catch (JsonProcessingException | RuntimeException ignored) {
            return e.getStatusText();
        }
    }
}
