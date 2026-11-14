package com.axedu.service.consult;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.axedu.dto.complaint.AiExplanationResponse;
import com.axedu.entity.enums.ComplaintTag;

/** 상담 엔진이 바깥에 묻는 것들 (선생님 정보, 규정·법령 설명, AI 해석). 테스트에서는 가짜로 바꿔 끼운다 */
public interface ConsultContext {

    /**
     * 받을 수 있는 선생님
     *
     * @param subjects 이 반에서 가르치는 과목 (담임은 다른 반 과목 포함)
     */
    record Teacher(String id, String name, boolean homeroom, List<String> subjects) {
    }

    List<Teacher> teachers();

    /** 방에서 시작해 정해진 교사 (없으면 null) */
    String fixedTeacherId();

    String teacherName(String id);

    /** 상담 내용으로 규정·법령 설명을 만들고 저장한다 (aiExplanation 에도 반영) */
    AiExplanationResponse explain(List<String> cardTexts, ComplaintTag category);

    /** 자유 입력 해석 (AI). 꺼져 있거나 실패하면 empty → 규칙으로 처리 */
    Optional<ConsultInterpretation> interpret(ConsultInterpretation.Request request);

    String newMessageId();

    OffsetDateTime now();
}
