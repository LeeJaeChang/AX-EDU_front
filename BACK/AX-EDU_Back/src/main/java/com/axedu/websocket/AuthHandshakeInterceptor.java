package com.axedu.websocket;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import com.axedu.common.exception.ApiException;
import com.axedu.common.security.AuthInterceptor;
import com.axedu.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

/** 쿼리 ?token= 으로 사용자 확인. 실패하면 401 로 핸드셰이크 거절 */
@Component
@RequiredArgsConstructor
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER = "authUser";

    private final AuthInterceptor authInterceptor;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("token");
        try {
            AuthUser user = authInterceptor.resolve(token == null ? null : java.net.URLDecoder.decode(token, java.nio.charset.StandardCharsets.UTF_8));
            attributes.put(ATTR_USER, user);
            return true;
        } catch (ApiException e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        // 없음
    }
}
