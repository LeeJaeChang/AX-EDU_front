package com.axedu.websocket;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/**
 * 사용자별 열린 WebSocket 세션 (여러 탭·기기 가능).
 * TODO: 서버를 여러 대로 늘리면 Redis Pub/Sub 등으로 다른 인스턴스에도 전달해야 한다.
 */
@Component
public class WebSocketSessionRegistry {

    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> decorated = new ConcurrentHashMap<>();

    public WebSocketSession add(String userId, WebSocketSession session) {
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, 5_000, 512 * 1024);
        decorated.put(session.getId(), safe);
        sessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(safe);
        return safe;
    }

    public void remove(String userId, WebSocketSession session) {
        WebSocketSession safe = decorated.remove(session.getId());
        Set<WebSocketSession> set = sessions.get(userId);
        if (set != null && safe != null) {
            set.remove(safe);
            if (set.isEmpty()) {
                sessions.remove(userId, set);
            }
        }
    }

    public WebSocketSession get(WebSocketSession raw) {
        return decorated.getOrDefault(raw.getId(), raw);
    }

    public Set<WebSocketSession> of(String userId) {
        return sessions.getOrDefault(userId, Set.of());
    }
}
