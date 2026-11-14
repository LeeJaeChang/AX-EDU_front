import { request } from './client';
import type {
  CardEditableFields,
  Complaint,
  ComplaintRecipient,
  CreateComplaintBody,
  Escalation,
  EscalationBody,
  ListComplaintsQuery,
  Page,
  Regulation,
  ReplyBody,
  ReplyDraft,
  ReplyDraftBody,
  ReplyResult,
  SummaryCardResult,
} from './types';

/** 민원·AI 챗봇 API (명세서 7장). 상태가 허용하지 않는 호출은 409 INVALID_COMPLAINT_STATUS. */
export interface ComplaintApi {
  /** STEP 1 '민원하기' → 최초 민원 + AI 규정 설명 */
  create(body: CreateComplaintBody, idempotencyKey: string): Promise<Complaint>;
  /** AI 설명 실패(422) 후 재시도 */
  retryExplanation(id: string): Promise<Complaint>;
  /** 상담 이어 가기 — 학부모의 답(자유 입력 또는 빠른 답 value)에 상담사가 다음 질문·설명을 돌려준다 (EXPLAINED 유지) */
  consult(id: string, content: string, value?: string): Promise<Complaint>;
  /** 받을 수 있는 선생님 목록 + AI 추천 */
  listRecipients(id: string): Promise<ComplaintRecipient[]>;
  /** 설명으로 이해, 종료 */
  close(id: string): Promise<Complaint>;
  /** STEP 2 '선생님과 대화하기' → 요약 카드 생성 */
  createSummaryCard(id: string): Promise<SummaryCardResult>;
  /** '수정사항 요청' — 자연어로 AI가 카드 재작성 */
  requestRevision(id: string, baseVersion: number, instruction: string): Promise<SummaryCardResult>;
  /** 카드 항목 직접 수정 */
  editSummaryCard(id: string, baseVersion: number, fields: CardEditableFields): Promise<SummaryCardResult>;
  /** '확인하고 보내기' — 고른 선생님과의 DIRECT 방(없으면 새로 만든다)에 카드를 올린다 */
  submit(id: string, cardVersion: number, teacherId: string, idempotencyKey: string): Promise<Complaint>;
  list(q?: ListComplaintsQuery): Promise<Page<Complaint>>;
  get(id: string): Promise<Complaint>;
  /** 교사가 카드 열람 SUBMITTED → TEACHER_REVIEWING */
  acknowledge(id: string): Promise<Complaint>;
  /** STEP 3 AI 입장 정리 도우미 (저장만, 발송 안 함) */
  createReplyDraft(id: string, body: ReplyDraftBody): Promise<ReplyDraft>;
  /** STEP 4 '학부모에게 보낼 연락' */
  reply(id: string, body: ReplyBody): Promise<ReplyResult>;
  /** STEP 5 '민원 대응팀 도움 요청' */
  escalate(id: string, body: EscalationBody): Promise<Escalation>;
  resolve(id: string): Promise<Complaint>;
  listRegulations(q?: string): Promise<Regulation[]>;
}

export const httpComplaintApi: ComplaintApi = {
  async create(body, idempotencyKey) {
    return (
      await request<Complaint>('/complaints', {
        method: 'POST',
        body,
        headers: { 'Idempotency-Key': idempotencyKey },
      })
    ).data;
  },
  async retryExplanation(id) {
    return (await request<Complaint>(`/complaints/${id}/explanation/retry`, { method: 'POST' })).data;
  },
  async consult(id, content, value) {
    return (await request<Complaint>(`/complaints/${id}/consultations`, { method: 'POST', body: { content, value } })).data;
  },
  async listRecipients(id) {
    return (await request<ComplaintRecipient[]>(`/complaints/${id}/recipients`)).data;
  },
  async close(id) {
    return (await request<Complaint>(`/complaints/${id}/close`, { method: 'POST' })).data;
  },
  async createSummaryCard(id) {
    return (await request<SummaryCardResult>(`/complaints/${id}/summary-card`, { method: 'POST' })).data;
  },
  async requestRevision(id, baseVersion, instruction) {
    return (
      await request<SummaryCardResult>(`/complaints/${id}/summary-card/revisions`, {
        method: 'POST',
        body: { baseVersion, instruction },
      })
    ).data;
  },
  async editSummaryCard(id, baseVersion, fields) {
    const { tags, ...rest } = fields;
    return (
      await request<SummaryCardResult>(`/complaints/${id}/summary-card`, {
        method: 'PATCH',
        body: { baseVersion, ...rest, ...(tags ? { basicInfo: { tags } } : {}) },
      })
    ).data;
  },
  async submit(id, cardVersion, teacherId, idempotencyKey) {
    return (
      await request<Complaint>(`/complaints/${id}/submit`, {
        method: 'POST',
        body: { cardVersion, teacherId },
        headers: { 'Idempotency-Key': idempotencyKey },
      })
    ).data;
  },
  async list(q = {}) {
    const { data, meta } = await request<Complaint[]>('/complaints', {
      query: { ...q, status: q.status?.join(',') },
    });
    return { items: data, nextCursor: meta.nextCursor ?? null, hasMore: !!meta.nextCursor };
  },
  async get(id) {
    return (await request<Complaint>(`/complaints/${id}`)).data;
  },
  async acknowledge(id) {
    return (await request<Complaint>(`/complaints/${id}/acknowledge`, { method: 'POST' })).data;
  },
  async createReplyDraft(id, body) {
    return (await request<ReplyDraft>(`/complaints/${id}/reply-drafts`, { method: 'POST', body })).data;
  },
  async reply(id, body) {
    return (await request<ReplyResult>(`/complaints/${id}/reply`, { method: 'POST', body })).data;
  },
  async escalate(id, body) {
    return (await request<Escalation>(`/complaints/${id}/escalations`, { method: 'POST', body })).data;
  },
  async resolve(id) {
    return (await request<Complaint>(`/complaints/${id}/resolve`, { method: 'POST' })).data;
  },
  async listRegulations(q) {
    return (await request<Regulation[]>('/regulations', { query: { q } })).data;
  },
};
