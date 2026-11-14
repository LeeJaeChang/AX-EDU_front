package com.axedu.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.axedu.common.exception.ApiException;
import com.axedu.common.exception.ErrorCode;
import com.axedu.common.response.ApiResponse;
import com.axedu.common.security.AuthUser;
import com.axedu.common.security.CurrentUser;
import com.axedu.config.WebConfig;
import com.axedu.dto.ai.KeywordExtractionRequest;
import com.axedu.dto.ai.KeywordExtractionResponse;
import com.axedu.dto.law.LawSearchResult;
import com.axedu.service.ComplaintService;
import com.axedu.service.ai.AiException;
import com.axedu.service.ai.keyword.KeywordExtractor;
import com.axedu.service.ai.keyword.KeywordModels;
import com.axedu.service.law.LawSearchClient;
import com.axedu.service.law.LawSearchException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 민원 키워드 추출 (AI 1단계, 계약서 외 추가 API). 민원 AI 흐름을 단계별로 확인·테스트하기 위한 것.
 * 실제 민원 흐름에서는 서비스가 {@link KeywordExtractor} 와 {@link LawSearchClient} 를 직접 호출한다.
 */
@Tag(name = "AI", description = "민원 AI 처리 단계 (1단계: 키워드 추출 → 법령 조회)")
@RestController
@RequestMapping(WebConfig.API_BASE + "/ai")
@RequiredArgsConstructor
public class AiKeywordController {

    private static final int DEFAULT_LAW_LIMIT = 6;

    private final KeywordExtractor keywordExtractor;
    private final LawSearchClient lawSearchClient;

    @Operation(summary = "민원 → 법령 검색 키워드 추출",
            description = "ChatGPT 가 민원(과 채팅 내역)에서 법령 검색용 키워드·쟁점·분류를 JSON 으로 뽑습니다. "
                    + "`searchLaws=true` 면 그 키워드로 법령 조회까지 합니다. "
                    + "`.env` 에 `OPENAI_API_KEY` 가 없으면 규칙 기반 대체 구현(`model: rule-based`)으로 동작합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422",
            description = "AI_PROCESSING_FAILED — AI 호출 실패 (details.aiErrorCode: UNAUTHORIZED·TIMEOUT·RATE_LIMITED 등)",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    @PostMapping("/keywords")
    public ApiResponse<KeywordExtractionResponse> extract(@CurrentUser AuthUser user, @RequestBody KeywordExtractionRequest req) {
        if (req == null || req.text() == null || req.text().isBlank()) {
            throw ApiException.validation("민원 내용을 입력해 주세요.", "text", "required");
        }
        if (req.text().length() > ComplaintService.MAX_TEXT) {
            throw ApiException.validation("민원 내용은 " + ComplaintService.MAX_TEXT + "자까지 입력할 수 있어요.", "text", "too_long");
        }

        KeywordModels.Result result;
        try {
            result = keywordExtractor.extract(new KeywordModels.Input(req.text(), req.history() == null ? List.of() : req.history()));
        } catch (AiException e) {
            throw new ApiException(ErrorCode.AI_PROCESSING_FAILED, e.getMessage(), Map.of("aiErrorCode", e.getErrorCode()));
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        List<LawSearchResult> laws = null;
        if (Boolean.TRUE.equals(req.searchLaws())) {
            if (result.keywords().isEmpty()) {
                laws = List.of();
                meta.put("lawSearch", "no_keywords");
            } else {
                int limit = req.lawLimit() == null ? DEFAULT_LAW_LIMIT : Math.max(1, Math.min(20, req.lawLimit()));
                try {
                    laws = lawSearchClient.search(result.terms(), limit);
                    meta.put("lawSearch", "ok");
                } catch (LawSearchException e) {
                    meta.put("lawSearch", "unavailable");
                }
            }
        }
        return ApiResponse.ok(KeywordExtractionResponse.of(result, laws), meta.isEmpty() ? null : meta);
    }
}
