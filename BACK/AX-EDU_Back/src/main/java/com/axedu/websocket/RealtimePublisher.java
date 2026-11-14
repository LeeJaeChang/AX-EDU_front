package com.axedu.websocket;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.axedu.common.util.TimeUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 이벤트 프레임 {"event","data","sentAt"} 전송.
 * 트랜잭션 안에서 부르면 커밋된 뒤에 보낸다 (롤백되면 보내지 않음).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimePublisher {

    private final WebSocketSessionRegistry registry;
    private final ObjectMapper objectMapper;

    public void publish(String userId, String event, Object data) {
        publish(List.of(userId), event, data);
    }

    public void publish(Collection<String> userIds, String event, Object data) {
        List<String> targets = List.copyOf(userIds);
        Runnable send = () -> sendNow(targets, event, data);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    private void sendNow(Collection<String> userIds, String event, Object data) {
        String json;
        try {
            Map<String, Object> frame = new LinkedHashMap<>();
            frame.put("event", event);
            frame.put("data", data);
            frame.put("sentAt", TimeUtil.now());
            json = objectMapper.writeValueAsString(frame);
        } catch (JsonProcessingException e) {
            log.error("failed to serialize event {}", event, e);
            return;
        }
        TextMessage message = new TextMessage(json);
        for (String userId : userIds) {
            for (WebSocketSession session : registry.of(userId)) {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(message);
                    }
                } catch (IOException | IllegalStateException e) {
                    log.debug("websocket send failed to {}: {}", userId, e.getMessage());
                }
            }
        }
        // TODO: 접속 중이 아닌 사용자에게는 notifications 테이블에 푸시를 쌓는다 (교사는 대화 가능 시간까지 deliver_after 보류)
    }
}
