package com.axedu.websocket;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.axedu.common.security.AuthUser;
import com.axedu.common.util.TimeUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 서버 → 클라이언트 이벤트 전달용. 클라이언트가 보내는 것은 30초마다 {"event":"ping"} 뿐 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionRegistry registry;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        registry.add(user(session).id(), session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node;
        try {
            node = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            return; // 알 수 없는 프레임은 무시
        }
        if ("ping".equals(node.path("event").asText())) {
            Map<String, Object> pong = new LinkedHashMap<>();
            pong.put("event", "pong");
            pong.put("sentAt", TimeUtil.now());
            registry.get(session).sendMessage(new TextMessage(objectMapper.writeValueAsString(pong)));
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("websocket transport error: {}", exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.remove(user(session).id(), session);
    }

    private AuthUser user(WebSocketSession session) {
        return (AuthUser) session.getAttributes().get(AuthHandshakeInterceptor.ATTR_USER);
    }
}
