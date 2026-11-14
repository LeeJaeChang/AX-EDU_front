import type {
  ComplaintStatus,
  ComplaintTag,
  ContactMethod,
  EscalationReason,
  EscalationStage,
  EscalationStatus,
  SourceType,
} from '../api';

export const TAG_LABEL: Record<ComplaintTag, string> = {
  LIFE_GUIDANCE: '생활지도',
  STUDY_EVALUATION: '학습·평가',
  PEER_RELATION: '교우관계',
  SAFETY: '안전',
  ETC: '기타',
};

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
