package com.axedu.common.security;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.axedu.common.exception.ApiException;
import com.axedu.common.exception.ErrorCode;
import com.axedu.entity.User;
import com.axedu.entity.enums.UserStatus;
import com.axedu.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/** Authorization: Bearer {token} 검증 → 요청 속성에 AuthUser 저장. 실패 시 401 UNAUTHORIZED */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR = "axedu.authUser";

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null;
        request.setAttribute(ATTR, resolve(token));
        return true;
    }

    /** 토큰 → AuthUser. WebSocket 핸드셰이크에서도 사용 */
    public AuthUser resolve(String token) {
        String userId = tokenProvider.parseUserId(token);
        if (userId == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        User user = userRepository.findById(userId)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        return new AuthUser(user.getId(), user.getRole(), user.getSchoolId(), user.getName());
    }
}
