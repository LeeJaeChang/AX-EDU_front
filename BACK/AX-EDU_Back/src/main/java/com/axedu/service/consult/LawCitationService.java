package com.axedu.service.consult;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.axedu.dto.law.LawSearchResult;
import com.axedu.entity.Regulation;
import com.axedu.entity.RegulationChunk;
import com.axedu.entity.enums.RegulationStatus;
import com.axedu.entity.enums.SourceType;
import com.axedu.repository.RegulationChunkRepository;
import com.axedu.repository.RegulationRepository;

import lombok.RequiredArgsConstructor;

/**
 * 법령 조회(AI_DATA) 결과를 인용할 수 있게 regulations·regulation_chunks 에 등록한다.
 * DB 설계서: school_id 가 NULL 인 regulations 는 전국 공통(법령). 청크 ID 는 AI_DATA 의 chunk_id 를 그대로 쓴다.
 */
@Service
@RequiredArgsConstructor
public class LawCitationService {

    private final RegulationRepository regulationRepository;
    private final RegulationChunkRepository chunkRepository;

    /** 이미 있으면 그대로, 없으면 등록. 등록된 청크 ID 목록 */
    @Transactional(propagation = Propagation.REQUIRED)
    public List<String> register(List<LawSearchResult> results) {
        for (LawSearchResult r : results) {
            if (r.chunkId() == null || r.chunkId().length() > 32 || chunkRepository.existsById(r.chunkId())) {
                continue;
            }
            String docId = docIdOf(r.chunkId());
            if (!regulationRepository.existsById(docId)) {
                Regulation doc = new Regulation();
                doc.setId(docId);
                doc.setSchoolId(null);
                doc.setSourceType(SourceType.LAW);
                doc.setTitle(clip(r.title(), 200));
                doc.setDocumentUrl(r.sourceUrl() == null || r.sourceUrl().isBlank() ? null : clip(r.sourceUrl(), 500));
                doc.setEffectiveFrom(dateOf(r.effectiveDate()));
                doc.setStatus(RegulationStatus.ACTIVE);
                regulationRepository.save(doc);
            }
            RegulationChunk chunk = new RegulationChunk();
            chunk.setId(r.chunkId());
            chunk.setRegulationId(docId);
            chunk.setArticleLabel(clip(labelOf(r), 100));
            chunk.setContent(r.text() == null ? "" : r.text());
            chunk.setSortOrder(0);
            chunkRepository.save(chunk);
        }
        return results.stream().map(LawSearchResult::chunkId).filter(chunkRepository::existsById).toList();
    }

    /** 'law-283357-18' → 'rdoc_law-283357' */
    static String docIdOf(String chunkId) {
        int i = chunkId.lastIndexOf('-');
        String key = i > 0 ? chunkId.substring(0, i) : chunkId;
        return clip("rdoc_" + key, 32);
    }

    /** '교원의 지위 향상 … 특별법 제19조(교육활동 침해행위)' / '대법원 2024도6945 … (판결요지)' */
    public static String labelOf(LawSearchResult r) {
        StringBuilder sb = new StringBuilder(r.title() == null ? "" : r.title());
        if (r.article() != null && !r.article().isBlank()) {
            sb.append(' ').append(r.article());
            if (r.articleTitle() != null && !r.articleTitle().isBlank()) {
                sb.append('(').append(r.articleTitle()).append(')');
            }
        } else if (r.articleTitle() != null && !r.articleTitle().isBlank()) {
            sb.append(" (").append(r.articleTitle()).append(')');
        }
        return sb.toString();
    }

    static LocalDate dateOf(String raw) {
        if (raw != null) {
            String d = raw.replace(".", "").replace("-", "").trim();
            try {
                if (d.length() == 8) {
                    return LocalDate.parse(d, DateTimeFormatter.BASIC_ISO_DATE);
                }
            } catch (DateTimeParseException ignored) {
                // 날짜 형식이 다르면 기본값
            }
        }
        return LocalDate.of(2000, 1, 1);
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
