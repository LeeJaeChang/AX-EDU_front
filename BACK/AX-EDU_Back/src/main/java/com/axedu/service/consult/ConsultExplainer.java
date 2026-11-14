package com.axedu.service.consult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.axedu.common.exception.ApiException;
import com.axedu.dto.complaint.AiExplanationResponse;
import com.axedu.dto.law.LawSearchResult;
import com.axedu.entity.Complaint;
import com.axedu.entity.ComplaintAiExplanation;
import com.axedu.entity.ComplaintExplanationCitation;
import com.axedu.entity.RegulationChunk;
import com.axedu.entity.enums.AiRecommendation;
import com.axedu.entity.enums.AiStatus;
import com.axedu.entity.enums.ComplaintTag;
import com.axedu.repository.ComplaintAiExplanationRepository;
import com.axedu.repository.ComplaintExplanationCitationRepository;
import com.axedu.repository.RegulationChunkRepository;
import com.axedu.service.ComplaintAssembler;
import com.axedu.service.ComplaintMasker;
import com.axedu.service.ai.AiException;
import com.axedu.service.ai.keyword.KeywordExtractor;
import com.axedu.service.ai.keyword.KeywordModels;
import com.axedu.service.ai.keyword.RuleBasedKeywordExtractor;
import com.axedu.service.law.LawSearchClient;
import com.axedu.service.law.LawSearchException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 상담 EXPLAIN 단계의 규정·법령 설명.
 *
 * <pre>
 * 상담 내용 → (AI 1단계) 법령 검색 키워드 → 법령 조회(AI_DATA, 파이썬) + 학교 규정(학칙·가정통신문)
 *          → (ChatGPT) 자료 안에서만 인용한 설명 → complaint_ai_explanations (aiExplanation 에도 반영)
 * </pre>
 *
 * AI 가 꺼져 있거나 실패하면 주제 규칙의 설명 + 같은 근거 자료로 만든다. 법령 조회 서버가 꺼져 있으면 학교 규정만.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsultExplainer {

    static final int LAW_LIMIT = 6;
    static final int MAX_CITATIONS = 4;

    private final KeywordExtractor keywordExtractor;
    private final LawSearchClient lawSearchClient;
    private final LawCitationService lawCitations;
    private final RegulationChunkRepository chunkRepository;
    private final ComplaintAiExplanationRepository explanationRepository;
    private final ComplaintExplanationCitationRepository citationRepository;
    private final ComplaintAssembler assembler;
    private final ComplaintMasker masker;
    private final ConsultAiClient ai;

    public AiExplanationResponse explain(Complaint c, List<String> cardTexts, ComplaintTag category, String facts) {
        long started = System.currentTimeMillis();
        List<String> masked = masker.mask(c, cardTexts);
        String consultation = String.join("\n", masked);
        List<ConsultRules.Rule> rules = ConsultRules.rulesOf(cardTexts, category);

        // 근거 자료: 학교 규정(주제 규칙) + 법령 조회(키워드)
        Map<String, RegulationChunk> school = new LinkedHashMap<>();
        chunkRepository.findAllById(ConsultRules.schoolRegulationsOf(rules)).forEach(ch -> school.put(ch.getId(), ch));
        List<LawSearchResult> laws = searchLaws(consultation);

        List<ConsultAiClient.Source> sources = new ArrayList<>();
        school.values().forEach(ch -> sources.add(new ConsultAiClient.Source(ch.getId(), "학교 규정", ch.getArticleLabel(), ch.getContent())));
        laws.forEach(l -> sources.add(new ConsultAiClient.Source(l.chunkId(), l.type(), LawCitationService.labelOf(l), l.text())));

        String empathy;
        String reason;
        String alternative;
        AiRecommendation recommendation;
        List<String> cited;
        String model;
        Optional<ConsultAiClient.Explanation> out = ai.explain(consultation, facts, sources);
        if (out.isPresent() && present(out.get().reason())) {
            ConsultAiClient.Explanation e = out.get();
            Set<String> allowed = sources.stream().map(ConsultAiClient.Source::id).collect(Collectors.toSet());
            cited = (e.citationIds() == null ? List.<String>of() : e.citationIds()).stream()
                    .filter(allowed::contains).distinct().limit(MAX_CITATIONS).toList();
            empathy = present(e.empathy()) ? e.empathy() : rules.get(0).empathy();
            reason = e.reason();
            alternative = present(e.alternative()) ? e.alternative() : rules.get(0).alternative();
            recommendation = "EXPLAINED".equals(e.recommendation()) ? AiRecommendation.EXPLAINED : AiRecommendation.TALK_TO_TEACHER;
            model = ai.model();
        } else {
            // 규칙: 주제 규칙의 설명 + 학교 규정 + 대표 법령 2건
            List<LawSearchResult> topLaws = laws.stream().filter(l -> "법령".equals(l.type()) || "행정규칙".equals(l.type())).limit(2).toList();
            cited = new ArrayList<>(school.keySet());
            topLaws.forEach(l -> cited.add(l.chunkId()));
            empathy = rules.get(0).empathy();
            reason = rules.stream().map(ConsultRules.Rule::reason).distinct().collect(Collectors.joining(" "));
            if (!topLaws.isEmpty()) {
                reason += " 관련 법령으로는 " + topLaws.stream().map(l -> "「" + LawCitationService.labelOf(l) + "」").collect(Collectors.joining(", "))
                        + "이 있어요. 이 일에 어떻게 적용되는지는 선생님이 사실을 확인한 뒤 안내해 드릴 거예요.";
            }
            alternative = rules.stream().map(ConsultRules.Rule::alternative).distinct().collect(Collectors.joining(" "));
            recommendation = rules.stream().anyMatch(r -> r.recommendation() == AiRecommendation.TALK_TO_TEACHER)
                    ? AiRecommendation.TALK_TO_TEACHER : AiRecommendation.EXPLAINED;
            model = RuleBasedKeywordExtractor.MODEL;
        }

        // 인용한 법령은 regulation_chunks 에 등록해야 근거 자료로 보여 줄 수 있다
        Set<String> citedSet = new LinkedHashSet<>(cited);
        lawCitations.register(laws.stream().filter(l -> citedSet.contains(l.chunkId())).toList());

        ComplaintAiExplanation row = new ComplaintAiExplanation();
        row.setComplaintId(c.getId());
        row.setAttempt((short) (explanationRepository.findFirstByComplaintIdOrderByAttemptDesc(c.getId())
                .map(ComplaintAiExplanation::getAttempt).orElse((short) 0) + 1));
        row.setStatus(AiStatus.SUCCEEDED);
        row.setEmpathy(clip(empathy, 500));
        row.setReason(reason);
        row.setAlternative(clip(alternative, 500));
        row.setRecommendation(recommendation);
        row.setModelName(clip(model, 50));
        row.setMaskedInputHash(ComplaintMasker.sha256(consultation));
        row.setLatencyMs((int) (System.currentTimeMillis() - started));
        row = explanationRepository.save(row);
        short order = 0;
        for (String id : citedSet) {
            if (chunkRepository.existsById(id)) {
                citationRepository.save(new ComplaintExplanationCitation(row.getId(), id, order++));
            }
        }
        return assembler.explanation(row);
    }

    /** 키워드(AI 1단계, 실패하면 규칙) → 법령 조회. 조회 서버가 꺼져 있으면 빈 목록 */
    List<LawSearchResult> searchLaws(String maskedConsultation) {
        List<String> terms;
        try {
            terms = keywordExtractor.extract(KeywordModels.Input.of(clip(maskedConsultation, 3000))).terms();
        } catch (AiException e) {
            terms = new RuleBasedKeywordExtractor().extract(KeywordModels.Input.of(maskedConsultation)).terms();
        }
        if (terms.isEmpty()) {
            return List.of();
        }
        try {
            return lawSearchClient.search(terms, LAW_LIMIT);
        } catch (LawSearchException | ApiException e) {
            log.info("법령 조회 없이 설명합니다: {}", e.getMessage());
            return List.of();
        }
    }

    private static boolean present(String s) {
        return s != null && !s.isBlank();
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
