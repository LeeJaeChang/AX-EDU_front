package com.axedu.dto.complaint;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "민원 작성 (학부모)")
public record CreateComplaintRequest(
        @Schema(description = "선택. 학부모 첫 화면(민원하기)에서 시작하면 비운다 → 받는 선생님은 제출할 때 고름. "
                + "주면 이전처럼 그 방의 선생님으로 고정", nullable = true) String roomId,
        @Schema(description = "자녀 학생 ID (자녀가 한 명이면 생략 가능)", example = "usr_s1") String studentId,
        @Schema(description = "민원 원문 (최대 2000자). 로컬 스텁에서 [AI실패] 를 넣으면 첫 AI 시도 실패", example = "아이가 수행평가 점수를 이해하지 못하고 있어요. 채점 기준을 알고 싶습니다.") String content,
        @Schema(description = "POST /files 로 받은 파일 ID 목록") List<String> attachmentIds,
        @Schema(description = "선택. 학부모가 처음에 고른 사전 분류 (LIFE_GUIDANCE·STUDY_EVALUATION·PEER_RELATION·SAFETY·ETC). "
                + "원문에서 주제를 못 찾았을 때의 기본값", example = "SAFETY", nullable = true) String category) {
}
