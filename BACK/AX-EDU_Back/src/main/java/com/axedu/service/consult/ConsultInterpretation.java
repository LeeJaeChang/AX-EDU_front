package com.axedu.service.consult;

import java.util.List;

/**
 * 학부모가 자유롭게 쓴 말을 AI 가 해석한 결과 (prompts/consult-turn/schema.json). 모르는 칸은 null.
 *
 * @param ack        상담사의 짧은 공감·확인 한두 문장 (판정 표현 없음). null 이면 규칙 문구
 * @param when       일시 (예: '어제 3교시')
 * @param place      장소·수업 (예: '영어 수업', '운동장')
 * @param impact     아이 상태 한 문장
 * @param request    학교에 바라는 점 한 문장
 * @param urgency    URGENT·SOON·NORMAL
 * @param methods    연락 방법 (MESSAGE·CALL·VISIT)
 * @param timeFrom   연락받기 좋은 시간 시작 HH:mm
 * @param timeTo     끝 HH:mm
 * @param timeAny    시간 무관
 * @param teacherId  선생님을 고른 경우 그 ID (후보 중에서만)
 * @param emotion    이 말의 감정 수준 CALM·CONCERNED·AGITATED
 */
public record ConsultInterpretation(String ack, String when, String place, String impact, String request, String urgency,
                                    List<String> methods, String timeFrom, String timeTo, Boolean timeAny, String teacherId,
                                    String emotion) {

    /**
     * @param pending    지금 묻고 있는 질문 키 (첫 민원이면 null)
     * @param question   그 질문 문장
     * @param text       학부모가 쓴 말 (가명처리됨)
     * @param context    지금까지 정리된 내용 요약 (가명처리됨)
     * @param teachers   고를 수 있는 선생님 (RECIPIENT 단계)
     * @param recommendedTeacherId 상담사가 추천한 선생님 ('추천해 주신 분' 해석용)
     */
    public record Request(String pending, String question, String text, String context, List<ConsultContext.Teacher> teachers,
                          String recommendedTeacherId) {
    }
}
