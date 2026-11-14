package com.axedu.service.law;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.axedu.common.exception.ApiException;
import com.axedu.config.AppProperties;
import com.axedu.dto.law.LawSearchRequest;
import com.axedu.dto.law.LawSearchResult;

import lombok.extern.slf4j.Slf4j;

/**
 * 키워드 기반 법령 조회. 파이썬 법령 조회 서버(ai/law_search_server.py)의 POST /search 를 호출한다.
 *
 * <p>민원 처리 흐름: 학부모 민원 → (AI) 키워드 추출 → <b>이 클라이언트로 법령 조회</b> → (AI) 조회 결과 + 민원으로 요약 카드 JSON
 */
@Slf4j
@Component
public class LawSearchClient {

    private final RestClient rest;

    public LawSearchClient(RestClient.Builder builder, AppProperties props) {
        AppProperties.LawSearch cfg = props.lawSearch();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(cfg.timeoutMillis()));
        factory.setReadTimeout(Duration.ofMillis(cfg.timeoutMillis()));
        // 본문을 버퍼링해 Content-Length 를 붙인다 (파이썬 http.server 는 chunked 본문을 읽지 못함)
        this.rest = builder.baseUrl(cfg.baseUrl()).requestFactory(new BufferingClientHttpRequestFactory(factory)).build();
    }

    /**
     * @return 관련도 순 결과 (없으면 빈 목록)
     * @throws ApiException        키워드가 비었거나 파이썬 서버가 요청을 거절함 (400 VALIDATION_ERROR)
     * @throws LawSearchException  파이썬 서버에 연결할 수 없거나 응답이 이상함
     */
    public List<LawSearchResult> search(LawSearchRequest request) {
        if (request == null || request.keywords() == null || request.keywords().stream().allMatch(k -> k == null || k.isBlank())) {
            throw ApiException.validation("검색 키워드를 한 개 이상 보내 주세요.", "keywords", "required");
        }
        try {
            SearchResponse body = rest.post()
                    .uri("/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new LawSearchException("법령 조회 서버 오류 (" + res.getStatusCode().value() + ")", null);
                    })
                    .body(SearchResponse.class);
            return body == null || body.results() == null ? List.of() : body.results();
        } catch (HttpClientErrorException.BadRequest e) {
            throw ApiException.validation(errorMessage(e), "request", "invalid");
        } catch (LawSearchException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("법령 조회 서버 호출 실패: {}", e.getMessage());
            throw new LawSearchException("법령 조회 서버에 연결할 수 없습니다.", e);
        }
    }

    /** 키워드만으로 조회 (자료 종류가 골고루 섞이게 perType 적용) */
    public List<LawSearchResult> search(List<String> keywords, int limit) {
        return search(LawSearchRequest.of(keywords, limit, Math.max(2, limit / 3)));
    }

    @SuppressWarnings("unchecked")
    private static String errorMessage(HttpClientErrorException e) {
        try {
            Map<String, Object> m = e.getResponseBodyAs(Map.class);
            if (m != null && m.get("error") instanceof String s) {
                return s;
            }
        } catch (RuntimeException ignored) {
            // 본문이 JSON 이 아니면 기본 문구
        }
        return "법령 조회 요청을 확인해 주세요.";
    }

    /** 파이썬 서버 응답 본문 */
    record SearchResponse(List<String> keywords, int count, List<LawSearchResult> results) {
    }
}
