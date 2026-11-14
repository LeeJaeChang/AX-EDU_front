package com.axedu.service.ai;

import java.time.LocalTime;
import java.util.List;

import com.axedu.entity.enums.AiRecommendation;
import com.axedu.entity.value.SituationItem;

/** AI 입출력 모델 */
public final class AiModels {

    private AiModels() {
    }

    /**
     * @param maskedText 이름·학년반을 가린 학부모 원문 (DB 설계서 6장: 가명처리한 입력만 전송)
     * @param schoolId   학교 규정 검색 범위
     * @param attempt    시도 순번 (1부터)
     */
    public record ExplanationInput(String maskedText, String schoolId, int attempt) {
    }

    /** citationChunkIds = 인용한 regulation_chunks.id */
    public record ExplanationResult(String empathy, String reason, String alternative, AiRecommendation recommendation,
                                    List<String> citationChunkIds) {
    }

    public record CardInput(String maskedText, String explanationReason, List<String> citationChunkIds) {
    }

    /** 요약 카드 내용 (버전·기본정보 제외) */
    public record CardDraft(List<String> tags, List<SituationItem> situation, List<String> concerns, List<String> requests,
                            List<String> preferredMethods, LocalTime preferredFrom, LocalTime preferredTo,
                            List<String> regulationChunkIds) {
    }

    public record ReplyDraftInput(String situation, String reason, String nextActions, String studentName, String teacherName,
                                  List<String> cardRequests) {
    }

    public record ReplyDraftResult(String draft, String situation, String reason, String nextActions) {
    }
}
