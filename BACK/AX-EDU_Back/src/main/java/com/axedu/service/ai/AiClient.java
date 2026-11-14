package com.axedu.service.ai;

import java.util.List;

import com.axedu.entity.value.CardSuggestion;
import com.axedu.service.ai.AiModels.CardDraft;
import com.axedu.service.ai.AiModels.CardInput;
import com.axedu.service.ai.AiModels.ExplanationInput;
import com.axedu.service.ai.AiModels.ExplanationResult;
import com.axedu.service.ai.AiModels.ReplyDraftInput;
import com.axedu.service.ai.AiModels.ReplyDraftResult;

/**
 * AI 기능 경계. 지금은 StubAiClient(규칙 기반 자리표시자)만 있다.
 * TODO(AI): 실제 모델(EXAONE 등) + 규정 검색(RAG) 구현체를 만들어 @Primary 로 등록하면 서비스 코드는 그대로 동작한다.
 */
public interface AiClient {

    String modelName();

    /** STEP 1 규정 기반 설명 */
    ExplanationResult explain(ExplanationInput input);

    /** STEP 2 요약 카드 생성 */
    CardDraft buildCard(CardInput input);

    /** '말로 수정 요청'. 이해하지 못하면 null */
    CardDraft revise(CardDraft base, String instruction);

    /** 감정 표현 재유입 시 순화 제안 (없으면 빈 목록) */
    List<CardSuggestion> suggest(CardDraft card);

    /** STEP 3 교사 답변 초안 */
    ReplyDraftResult draftReply(ReplyDraftInput input);
}
