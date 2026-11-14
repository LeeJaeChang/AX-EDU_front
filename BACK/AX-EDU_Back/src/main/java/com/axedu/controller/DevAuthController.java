package com.axedu.controller;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.axedu.common.response.ApiResponse;
import com.axedu.common.security.JwtTokenProvider;
import com.axedu.config.WebConfig;
import com.axedu.entity.User;
import com.axedu.entity.enums.UserRole;
import com.axedu.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 개발용 토큰 발급 (local 프로필에서만 켜짐). 운영 토큰은 슈퍼스쿨이 발급한다.
 * 예: GET /api/axedu/v1/dev/token?userId=usr_t1 → 프론트 localStorage['axedu.accessToken'] 에 넣어 사용
 */
@Tag(name = "Dev", description = "개발용 토큰 발급 (local 프로필 전용)")
@Profile("local")
@RestController
@RequestMapping(WebConfig.API_BASE + "/dev")
@RequiredArgsConstructor
public class DevAuthController {

    private final UserService userService;
    private final JwtTokenProvider tokenProvider;

    @Operation(summary = "개발용 토큰 발급",
            description = "시드 사용자 ID 로 JWT 를 발급합니다. 받은 `accessToken` 을 Swagger 오른쪽 위 **Authorize** 에 넣으세요.")
    @SecurityRequirements
    @GetMapping("/token")
    public ApiResponse<Map<String, String>> token(
            @Parameter(description = "시드 사용자 ID (usr_t1 교사, usr_p1 학부모, usr_s1 학생, usr_rt1 대응팀)", example = "usr_t1")
            @RequestParam String userId) {
        User u = userService.get(userId);
        String token = tokenProvider.issue(u.getId(), u.getRole().name(), u.getSchoolId(), u.getName(),
                u.getRole() == UserRole.PARENT ? userService.childStudentIds(u.getId()) : null);
        return ApiResponse.ok(Map.of("accessToken", token));
    }
}
