package com.axedu.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.axedu.common.security.AuthUser;
import com.axedu.dto.regulation.RegulationResponse;
import com.axedu.entity.Regulation;
import com.axedu.entity.RegulationChunk;
import com.axedu.repository.RegulationChunkRepository;
import com.axedu.repository.RegulationRepository;

import lombok.RequiredArgsConstructor;

/**
 * 규정(근거 자료).
 * - search(): GET /regulations — 차후 작성 예정 (지금은 빈 목록)
 * - toResponses(): AI 설명·요약 카드에 붙은 조항을 Regulation 모양으로 바꾸는 것만 구현
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegulationService {

    private static final int EXCERPT_MAX = 200;

    private final RegulationRepository regulationRepository;
    private final RegulationChunkRepository chunkRepository;

    /**
     * GET /regulations?q=
     * TODO(규정 API): school_id = 내 학교 또는 NULL(법령), status = ACTIVE 인 문서의 조항을 제목·원문 부분 일치로 검색
     *                 (regulation_chunks 의 pg_trgm GIN 인덱스 사용). 등록·삭제(POST·DELETE /regulations)도 함께 작성 예정.
     */
    public List<RegulationResponse> search(AuthUser viewer, String q) {
        return List.of();
    }

    /** 조항 ID 목록 → Regulation 응답 (순서 유지, 없는 ID 는 건너뜀) */
    public List<RegulationResponse> toResponses(Collection<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return List.of();
        }
        Map<String, RegulationChunk> chunks = chunkRepository.findAllById(chunkIds).stream()
                .collect(Collectors.toMap(RegulationChunk::getId, Function.identity()));
        Map<String, Regulation> docs = regulationRepository.findAllById(
                chunks.values().stream().map(RegulationChunk::getRegulationId).distinct().toList()).stream()
                .collect(Collectors.toMap(Regulation::getId, Function.identity()));
        Map<String, RegulationResponse> ordered = new LinkedHashMap<>();
        for (String id : chunkIds) {
            RegulationChunk c = chunks.get(id);
            Regulation doc = c == null ? null : docs.get(c.getRegulationId());
            if (c != null && doc != null) {
                ordered.put(id, new RegulationResponse(c.getId(), doc.getSourceType(), c.getArticleLabel(),
                        excerpt(c.getContent()), doc.getDocumentUrl()));
            }
        }
        return new ArrayList<>(ordered.values());
    }

    /** 존재하는 조항 ID 만 남긴다 (AI 가 없는 ID 를 인용해도 FK 오류가 나지 않게) */
    public List<String> existingChunkIds(Collection<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return List.of();
        }
        var found = chunkRepository.findAllById(chunkIds).stream().map(RegulationChunk::getId).collect(Collectors.toSet());
        return chunkIds.stream().distinct().filter(found::contains).toList();
    }

    private static String excerpt(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= EXCERPT_MAX ? content : content.substring(0, EXCERPT_MAX) + " …";
    }
}
