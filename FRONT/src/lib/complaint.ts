import type {
  ComplaintStatus,
  ComplaintTag,
  ContactMethod,
  EmotionLevel,
  EmotionTrend,
  EscalationReason,
  EscalationStage,
  EscalationStatus,
  SourceType,
  UrgencyLevel,
} from '../api';

export const TAG_LABEL: Record<ComplaintTag, string> = {
  LIFE_GUIDANCE: '생활지도',
  STUDY_EVALUATION: '학습·평가',
  PEER_RELATION: '교우관계',
  SAFETY: '안전',
  ETC: '기타',
};

export const URGENCY_LABEL: Record<UrgencyLevel, string> = {
  URGENT: '긴급 · 오늘 안에 확인',
  SOON: '며칠 안에 답변',
  NORMAL: '급하지 않음',
};

export const EMOTION_LABEL: Record<EmotionLevel, string> = {
  CALM: '차분함',
  CONCERNED: '걱정·속상함',
  AGITATED: '격양됨',
};

export const EMOTION_TREND_LABEL: Record<EmotionTrend, string> = {
  EASING: '상담하며 차분해짐',
  RISING: '상담하며 더 격해짐',
  STEADY: '처음과 비슷함',
};

/**
 * 민원하기 첫 화면의 사전 분류 질문 (자세한 이야기를 듣기 전에 고르는 예시 선택지).
 * 고르면 상담사가 분류에 맞는 안내로 자세한 이야기를 청한다.
 */
export const CATEGORY_OPTIONS: { tag: ComplaintTag; label: string; prompt: string; placeholder: string }[] = [
  {
    tag: 'LIFE_GUIDANCE',
    label: '생활지도 (휴대전화·복장·출결)',
    prompt: '생활지도에 관한 일이군요. 휴대전화 보관, 복장 지도, 출결 처리처럼 어떤 일이 있었는지 편하게 들려주세요.',
    placeholder: '예: 수업 중에 아이 휴대전화를 가져가셔서 하루 종일 연락이 안 됐어요.',
  },
  {
    tag: 'STUDY_EVALUATION',
    label: '수업·평가 (수행평가·성적)',
    prompt: '수업이나 평가에 관한 일이군요. 어떤 과목의 어떤 평가였는지, 어떤 점이 마음에 걸리셨는지 들려주세요.',
    placeholder: '예: 영어 수행평가 점수가 낮게 나왔는데 감점 이유를 모르겠어요.',
  },
  {
    tag: 'PEER_RELATION',
    label: '친구 관계',
    prompt: '친구 관계로 마음 쓰이시는군요. 아이가 어떤 일을 겪었는지 천천히 들려주세요.',
    placeholder: '예: 반 친구들이 아이를 계속 놀린다고 해요.',
  },
  {
    tag: 'SAFETY',
    label: '안전·사고 (다침·위험)',
    prompt: '아이의 안전에 관한 일이군요. 많이 놀라셨을 것 같아요. 어떤 일이 있었는지 들려주세요.\n지금 당장 위험한 상황이라면 112·119나 학교 교무실로 먼저 연락해 주세요.',
    placeholder: '예: 체육 시간에 아이가 넘어져 다쳤는데 연락을 못 받았어요.',
  },
  {
    tag: 'ETC',
    label: '그 밖의 일',
    prompt: '어떤 일이든 괜찮아요. 있었던 일을 편하게 들려주세요.',
    placeholder: '있었던 일을 편하게 적어 주세요.',
  },
];

export const CONTACT_LABEL: Record<ContactMethod, string> = {
  MESSAGE: '채팅',
  CALL: '안심전화',
  VISIT: '방문 면담',
};

export const SOURCE_LABEL: Record<SourceType, string> = {
  SCHOOL_RULE: '학칙',
  NOTICE_LETTER: '가정통신문',
  LAW: '법령',
};

export const REASON_LABEL: Record<EscalationReason, { label: string; hint: string }> = {
  REPEATED_DEMAND: { label: '같은 요구 반복', hint: '답변 뒤에도 같은 요구가 계속됨' },
  ABUSE: { label: '폭언·협박', hint: '모욕적인 말, 위협' },
  OUT_OF_HOURS_PRESSURE: { label: '근무시간 외 연락 강요', hint: '밤·주말 연락 요구' },
  BEYOND_AUTHORITY: { label: '교사 권한 밖 요구', hint: '반 교체, 규정 변경 등' },
  ETC: { label: '기타', hint: '' },
};

export const STAGE_LABEL: Record<EscalationStage, string> = {
  RESPONSE_TEAM: '민원대응팀',
  VICE_PRINCIPAL: '교감',
  PRINCIPAL: '교장',
};

export const ESC_STATUS_LABEL: Record<EscalationStatus, string> = {
  REQUESTED: '접수됨',
  IN_PROGRESS: '처리 중',
  RESOLVED: '처리 완료',
};

type Tone = 'accent' | 'mark' | 'ok' | 'warn' | 'neutral';

/** 교사가 보는 상태 */
export const TEACHER_STATUS: Partial<Record<ComplaintStatus, { label: string; tone: Tone }>> = {
  SUBMITTED: { label: '새 민원', tone: 'accent' },
  TEACHER_REVIEWING: { label: '답변 준비 중', tone: 'mark' },
  IN_CONVERSATION: { label: '답변 보냄', tone: 'ok' },
  ESCALATED: { label: '대응팀 도움 요청', tone: 'warn' },
  RESOLVED: { label: '처리 종료', tone: 'neutral' },
};

/** 학부모가 보는 상태. 이관 여부는 미결 사항이라 학부모에게는 드러내지 않는다. */
export const PARENT_STATUS: Record<ComplaintStatus, { label: string; tone: Tone }> = {
  EXPLAINED: { label: 'AI와 상담 중', tone: 'mark' },
  CLOSED_BY_EXPLANATION: { label: '설명으로 종료', tone: 'neutral' },
  CARD_DRAFTING: { label: '요약 카드 작성 중', tone: 'mark' },
  SUBMITTED: { label: '선생님께 전달됨', tone: 'accent' },
  TEACHER_REVIEWING: { label: '선생님이 확인 중', tone: 'accent' },
  IN_CONVERSATION: { label: '선생님 답변 도착', tone: 'ok' },
  ESCALATED: { label: '학교에서 확인 중', tone: 'accent' },
  RESOLVED: { label: '처리 종료', tone: 'neutral' },
};

export const isDrafting = (s: ComplaintStatus) => s === 'EXPLAINED' || s === 'CARD_DRAFTING';
