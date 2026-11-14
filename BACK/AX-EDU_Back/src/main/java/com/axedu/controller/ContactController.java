package com.axedu.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.axedu.common.response.ApiResponse;
import com.axedu.common.security.AuthUser;
import com.axedu.common.security.CurrentUser;
import com.axedu.config.WebConfig;
import com.axedu.dto.chat.ContactResponse;
import com.axedu.entity.enums.UserRole;
import com.axedu.service.ContactService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 새 대화 상대 (계약서 3.2) — 페이지네이션 없음 */
@Tag(name = "대화 상대", description = "새 대화를 시작할 수 있는 상대 목록 (계약서 3.2)")
@RestController
@RequestMapping(WebConfig.API_BASE + "/chat/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;

    @Operation(summary = "대화 상대 목록", description = "내가 대화를 시작할 수 있는 상대를 조회합니다. 페이지네이션 없음.")
    @GetMapping
    public ApiResponse<List<ContactResponse>> list(@CurrentUser AuthUser user,
                                                   @Parameter(description = "이름 검색어") @RequestParam(required = false) String q,
                                                   @Parameter(description = "역할 필터") @RequestParam(required = false) UserRole role,
                                                   @Parameter(description = "학년", example = "3") @RequestParam(required = false) Integer grade,
                                                   @Parameter(description = "반", example = "9") @RequestParam(required = false) Integer classNo) {
        return ApiResponse.ok(contactService.list(user, q, role, grade, classNo));
    }
}
