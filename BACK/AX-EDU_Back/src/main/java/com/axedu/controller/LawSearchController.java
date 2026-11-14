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
import com.axedu.dto.law.LawSearchRequest;
import com.axedu.dto.law.LawSearchResult;
import com.axedu.service.law.LawSearchClient;
import com.axedu.service.law.LawSearchException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 키워드 기반 법령 조회 (계약서 외 추가 API). 민원 AI 흐름의 중간 단계를 단독으로 확인·테스트하기 위한 것.
 * 실제 민원 흐름에서는 서비스가 {@link LawSearchClient} 를 직접 호출한다.
 */
@Tag(name = "법령 조회", description = "키워드로 법령·고시·판례·결정례 조회 (AI_DATA 기반, 파이썬 법령 조회 서버 필요)")
@RestController
@RequestMapping(WebConfig.API_BASE + "/laws")
@RequiredArgsConstructor
public class LawSearchController {

    private final LawSearchClient lawSearchClient;

    @Operation(summary = "키워드로 법령 조회",
            description = "AI 가 민원에서 뽑은 키워드로 관련 조문·판례 단락을 관련도 순으로 돌려줍니다. "
                    + "`meta.count` 에 결과 수. 파이썬 서버(`python3 ai/law_search_server.py`)가 꺼져 있으면 500 `INTERNAL_ERROR`.")
    @PostMapping("/search")
    public ApiResponse<List<LawSearchResult>> search(@CurrentUser AuthUser user, @RequestBody LawSearchRequest req) {
        try {
            List<LawSearchResult> results = lawSearchClient.search(req);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("count", results.size());
            return ApiResponse.ok(results, meta);
        } catch (LawSearchException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "법령 조회 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        }
    }
}
