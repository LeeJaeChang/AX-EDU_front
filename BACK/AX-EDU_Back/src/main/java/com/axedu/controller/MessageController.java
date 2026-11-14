package com.axedu.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.axedu.common.response.ApiResponse;
import com.axedu.common.response.PageMeta;
import com.axedu.common.security.AuthUser;
import com.axedu.common.security.CurrentUser;
import com.axedu.config.WebConfig;
import com.axedu.dto.chat.MessageResponse;
import com.axedu.dto.chat.ReadRequest;
import com.axedu.dto.chat.SendMessageRequest;
import com.axedu.service.MessageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 메시지 (계약서 3.7, 3.8, 3.10) */
@Tag(name = "메시지", description = "메시지 목록·전송·읽음 처리 (계약서 3.7, 3.8, 3.10)")
@RestController
@RequestMapping(WebConfig.API_BASE + "/chat/rooms/{roomId}")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /** 항상 최신순 + meta.hasMore */
    @Operation(summary = "메시지 목록",
            description = "항상 최신순으로 내려 주고 `meta.hasMore` 로 더 있는지 알려 줍니다. `before` 로 과거, `after` 로 새 메시지를 받습니다.")
    @GetMapping("/messages")
    public ApiResponse<List<MessageResponse>> list(@CurrentUser AuthUser user,
                                                   @Parameter(description = "채팅방 ID", example = "room_rh") @PathVariable String roomId,
                                                   @Parameter(description = "이 메시지 ID 보다 이전 것") @RequestParam(required = false) String before,
                                                   @Parameter(description = "이 메시지 ID 보다 이후 것") @RequestParam(required = false) String after,
                                                   @Parameter(description = "페이지 크기", example = "30") @RequestParam(required = false) Integer limit) {
        MessageService.MessagePage page = messageService.list(user, roomId, before, after, limit);
        return ApiResponse.ok(page.items(), PageMeta.cursor(page.nextCursor(), page.hasMore()));
    }

    /** 새로 저장하면 201, 같은 clientMessageId 재전송이면 기존 메시지를 200 으로 */
    @Operation(summary = "메시지 보내기",
            description = "새로 저장하면 201, 같은 `clientMessageId` 를 다시 보내면 기존 메시지를 200 으로 돌려줍니다. "
                    + "첨부만 있으면 서버가 `IMAGE`/`FILE` 로 type 을 정합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "새 메시지 저장됨")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "같은 clientMessageId 재전송 — 기존 메시지")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
            description = "FORBIDDEN / CHAT_OUTSIDE_AVAILABLE_HOURS — 대화 가능 시간 아님 / NOTICE_ROOM_READ_ONLY — 공지방",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    @PostMapping("/messages")
    public ResponseEntity<ApiResponse<MessageResponse>> send(@CurrentUser AuthUser user,
                                                             @Parameter(description = "채팅방 ID", example = "room_rh") @PathVariable String roomId,
                                                             @Parameter(description = "중복 전송 방지 키 (선택)") @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                                             @RequestBody SendMessageRequest req) {
        MessageService.SendResult result = messageService.send(user, roomId, req, idempotencyKey);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(ApiResponse.ok(result.message()));
    }

    @Operation(summary = "읽음 처리", description = "`lastReadMessageId` 까지 읽음 처리합니다. 본문을 생략하면 최신 메시지까지 읽음.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "처리됨")
    @PostMapping("/read")
    public ResponseEntity<Void> read(@CurrentUser AuthUser user,
                                     @Parameter(description = "채팅방 ID", example = "room_rh") @PathVariable String roomId,
                                     @RequestBody(required = false) ReadRequest req) {
        messageService.markRead(user, roomId, req);
        return ResponseEntity.noContent().build();
    }
}
