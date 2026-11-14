package com.axedu.config;

import java.util.Map;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.axedu.common.security.AuthUser;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Swagger(OpenAPI 3) 문서 설정.
 * <ul>
 *   <li>Swagger UI: {@code /swagger-ui.html}</li>
 *   <li>OpenAPI JSON: {@code /v3/api-docs}</li>
 * </ul>
 * 모든 API 는 기본으로 Bearer JWT 를 요구하고, 인증이 없는 API 는 컨트롤러에서 {@code @SecurityRequirements()} 로 뺀다.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";
    private static final String ERROR_SCHEMA = "ErrorResponse";

    static {
        // @CurrentUser AuthUser 는 인증 인터셉터가 넣어 주는 값이라 요청 파라미터로 노출하지 않는다
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(AuthUser.class);
    }

    private static final String DESCRIPTION = """
            AX-EDU 채팅·민원 API 서버 문서입니다. 프론트엔드 API 계약서 기준으로 작성되었습니다.

            ### 인증 (로컬)
            1. **Dev** 그룹의 `GET /dev/token?userId=usr_t1` 을 실행해 `accessToken` 을 받습니다. (local 프로필 전용)
            2. 오른쪽 위 **Authorize** 버튼에 토큰을 붙여 넣습니다. (`Bearer ` 접두사 없이)

            시드 계정: `usr_t1`(교사 김수학, 3-9 담임) · `usr_t2`(교사 이영어) · `usr_p1`·`usr_p2`(학부모) ·
            `usr_s1`~`usr_s6`(학생) · `usr_rt1`(민원대응팀). 시드 방 예시 `room_rh`, `room_p1`, 시드 민원 `cmp_seed1`.

            ### 공통 응답 봉투
            - 성공: `{"success": true, "data": ..., "meta": {...}}`
            - 실패: `{"success": false, "error": {"code", "message", "details"}}`
            - 목록 `meta`: `nextCursor`(끝이면 null), `hasMore`

            ### 에러 코드
            | HTTP | code | 설명 |
            | --- | --- | --- |
            | 400 | `VALIDATION_ERROR` | 입력값 오류 (`details.fields`) |
            | 401 | `UNAUTHORIZED` | 토큰 없음·만료·잘못됨 |
            | 403 | `FORBIDDEN` | 권한 없음 |
            | 403 | `CHAT_OUTSIDE_AVAILABLE_HOURS` | 대화 가능 시간이 아님 |
            | 403 | `NOTICE_ROOM_READ_ONLY` | 공지형 채팅방에 전송 시도 |
            | 404 | `NOT_FOUND` | 대상 없음 |
            | 409 | `ROOM_ALREADY_EXISTS` | 이미 대화 중인 상대 (`details.roomId`) |
            | 409 | `INVALID_COMPLAINT_STATUS` | 지금 민원 상태에서 불가 / 카드 버전 충돌 (`details.latestVersion`) |
            | 413 | `FILE_TOO_LARGE` | 파일 20MB 초과 |
            | 422 | `AI_PROCESSING_FAILED` | AI 처리 실패 (`details.complaintId`) |
            | 429 | `RATE_LIMITED` | 요청 과다 (`Retry-After` 헤더) |
            | 500 | `INTERNAL_ERROR` | 서버 오류 |

            ### 실시간 (WebSocket, 문서 범위 밖)
            `ws://{host}/ws/axedu/v1?token={accessToken}` — `message.created`, `message.read`, `room.created`, `room.updated`,
            `room.unread_changed`, `complaint.status_changed`, `complaint.card_updated`, `escalation.created`, `escalation.updated`
            """;

    @Bean
    public OpenAPI axeduOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AX-EDU Backend API")
                        .version("v1")
                        .description(DESCRIPTION))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("`GET /dev/token` 으로 받은 accessToken"))
                        .addSchemas(ERROR_SCHEMA, errorSchema()))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    /** 모든 API 에 공통 에러 응답(401·500, 인증 API 는 403·404 포함)을 붙인다. 이미 적힌 응답은 그대로 둔다. */
    @Bean
    public OpenApiCustomizer commonErrorResponses() {
        return openApi -> openApi.getPaths().values().forEach(path -> path.readOperations().forEach(op -> {
            ApiResponses responses = op.getResponses();
            boolean secured = op.getSecurity() == null || !op.getSecurity().isEmpty();
            if (secured) {
                responses.putIfAbsent("401", error("UNAUTHORIZED — 토큰 없음·만료"));
                responses.putIfAbsent("403", error("FORBIDDEN — 권한 없음"));
            }
            responses.putIfAbsent("500", error("INTERNAL_ERROR — 서버 오류"));
        }));
    }

    private static ApiResponse error(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA))));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Schema<?> errorSchema() {
        Schema body = new ObjectSchema()
                .addProperty("code", new StringSchema().example("NOT_FOUND"))
                .addProperty("message", new StringSchema().example("요청한 정보를 찾을 수 없습니다."))
                .addProperty("details", new ObjectSchema().additionalProperties(true).example(Map.of()));
        return new ObjectSchema()
                .description("실패 응답 봉투")
                .addProperty("success", new Schema<Boolean>().type("boolean").example(false))
                .addProperty("error", body);
    }
}
