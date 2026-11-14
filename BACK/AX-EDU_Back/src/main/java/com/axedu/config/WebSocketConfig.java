package com.axedu.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.axedu.websocket.AuthHandshakeInterceptor;
import com.axedu.websocket.RealtimeWebSocketHandler;

import lombok.RequiredArgsConstructor;

/** wss://{host}/ws/axedu/v1?token={accessToken} (계약서 5장) */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final RealtimeWebSocketHandler handler;
    private final AuthHandshakeInterceptor handshakeInterceptor;
    private final AppProperties props;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/axedu/v1")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns(props.cors().allowedOrigins().toArray(String[]::new));
    }
}
