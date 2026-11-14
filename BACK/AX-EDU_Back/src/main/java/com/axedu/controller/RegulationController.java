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
import com.axedu.dto.regulation.RegulationResponse;
import com.axedu.service.RegulationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** GET /regulations (계약서 4.13). TODO(규정 API): 검색·등록·삭제 차후 작성 — 지금은 빈 목록 */
@Tag(name = "규정", description = "학교 규정 검색 (계약서 4.13) — 아직 스텁")
@RestController
@RequestMapping(WebConfig.API_BASE + "/regulations")
@RequiredArgsConstructor
public class RegulationController {

    private final RegulationService regulationService;

    @Operation(summary = "규정 검색", description = "**스텁** — 지금은 항상 빈 목록을 돌려줍니다. 검색·등록·삭제는 차후 작성 예정.")
    @GetMapping
    public ApiResponse<List<RegulationResponse>> search(@CurrentUser AuthUser user,
                                                        @Parameter(description = "검색어") @RequestParam(required = false) String q) {
        return ApiResponse.ok(regulationService.search(user, q));
    }
}
