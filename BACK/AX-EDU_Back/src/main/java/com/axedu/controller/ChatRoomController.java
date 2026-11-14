package com.axedu.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.axedu.common.response.ApiResponse;
import com.axedu.common.response.PageMeta;
import com.axedu.common.security.AuthUser;
import com.axedu.common.security.CurrentUser;
import com.axedu.config.WebConfig;
import com.axedu.dto.chat.ChatRoomDetailResponse;
import com.axedu.dto.chat.ChatRoomResponse;
import com.axedu.dto.chat.CreateRoomRequest;
import com.axedu.dto.chat.UpdateRoomSettingsRequest;
import com.axedu.entity.enums.RoomType;
import com.axedu.service.ChatRoomService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 채팅방 (계약서 3.1, 3.3~3.6) */
@Tag(name = "채팅방", description = "채팅방 목록·생성·상세·설정·나가기 (계약서 3.1, 3.3~3.6)")
@RestController
@RequestMapping(WebConfig.API_BASE + "/chat/rooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    @Operation(summary = "채팅방 목록", description = "내가 참여 중인 채팅방을 최근 메시지 순으로 조회합니다. `meta.nextCursor` 로 다음 페이지를 이어 받습니다.")
    @GetMapping
    public ApiResponse<List<ChatRoomResponse>> list(@CurrentUser AuthUser user,
                                                    @Parameter(description = "방 이름·상대 이름 검색어") @RequestParam(required = false) String q,
                                                    @Parameter(description = "방 종류 필터") @RequestParam(required = false) RoomType type,
                                                    @Parameter(description = "이전 응답의 meta.nextCursor") @RequestParam(required = false) String cursor,
                                                    @Parameter(description = "페이지 크기", example = "20") @RequestParam(required = false) Integer limit) {
        ChatRoomService.RoomPage page = chatRoomService.list(user, q, type, cursor, limit);
        return ApiResponse.ok(page.items(), PageMeta.cursor(page.nextCursor()));
    }

    @Operation(summary = "1:1 채팅방 만들기", description = "상대(`counterpartId`)와 1:1 방을 만듭니다. 이미 대화 중이면 409 `ROOM_ALREADY_EXISTS` + `details.roomId`.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "생성됨")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "ROOM_ALREADY_EXISTS — 이미 대화 중인 상대",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    @PostMapping
    public ResponseEntity<ApiResponse<ChatRoomResponse>> create(@CurrentUser AuthUser user, @RequestBody CreateRoomRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(chatRoomService.create(user, req == null ? null : req.counterpartId())));
    }

    @Operation(summary = "채팅방 상세", description = "방 정보와 참여자, 대화 가능 시간 등을 조회합니다.")
    @GetMapping("/{roomId}")
    public ApiResponse<ChatRoomDetailResponse> detail(@CurrentUser AuthUser user,
                                                      @Parameter(description = "채팅방 ID", example = "room_rh") @PathVariable String roomId) {
        return ApiResponse.ok(chatRoomService.detail(user, roomId));
    }

    @Operation(summary = "채팅방 설정 변경", description = "보낸 키만 바꿉니다. `availableHoursOverride` 를 `null` 로 보내면 교사 기본 설정으로 되돌립니다.")
    @PatchMapping("/{roomId}/settings")
    public ApiResponse<ChatRoomResponse> updateSettings(@CurrentUser AuthUser user,
                                                        @Parameter(description = "채팅방 ID", example = "room_rh") @PathVariable String roomId,
                                                        @RequestBody UpdateRoomSettingsRequest req) {
        return ApiResponse.ok(chatRoomService.updateSettings(user, roomId, req));
    }

    @Operation(summary = "채팅방 나가기")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "나감")
    @DeleteMapping("/{roomId}/members/me")
    public ResponseEntity<Void> leave(@CurrentUser AuthUser user,
                                      @Parameter(description = "채팅방 ID", example = "room_rh") @PathVariable String roomId) {
        chatRoomService.leave(user, roomId);
        return ResponseEntity.noContent().build();
    }
}
