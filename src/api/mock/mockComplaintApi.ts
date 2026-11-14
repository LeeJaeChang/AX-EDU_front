import { toMs } from '../../lib/format';
import { ApiError } from '../client';
import type { ComplaintApi } from '../complaintApi';
import type {
  CardEditableFields,
  Complaint,
  ConsultMessage,
  ComplaintStatus,
  Escalation,
  SummaryCard,
  SummaryCardResult,
  UserSummary,
} from '../types';
import { draftReply, REGULATIONS, revise, suggestionsFor } from './mockAi';
import { cardFromConsult, continueConsult, emptyConsult, RECIPIENTS, startConsult } from './mockConsult';
import {
  changeStatus,
  clone,
  complaints,
  delay,
  emit,
  ensureDirect,
  genId,
  me,
  nowIso,
  postMessage,
  PRE_SUBMIT,
  rooms,
  teacherOf,
  users,
  type CardRow,
  type CardSource,
  type ComplaintRow,
} from './store';
import './mockChatApi'; // 시드 데이터 보장

const AI_DELAY = 1200;
const MAX_TURNS = 40;
const idempotency = new Map<string, string>();
const ctx = { users, now: nowIso, genId };

function checkContent(raw: string) {
  const content = raw.trim();
  if (!content) throw new ApiError(400, 'VALIDATION_ERROR', '내용을 입력해 주세요.', { fields: { content: 'required' } });
  if (content.length > 2000) throw new ApiError(400, 'VALIDATION_ERROR', '2,000자까지 쓸 수 있어요.', { fields: { content: 'max 2000' } });
  return content;
}

function invalid(c: ComplaintRow, action: string): never {
  throw new ApiError(409, 'INVALID_COMPLAINT_STATUS', `현재 상태(${c.status})에서는 ${action}할 수 없습니다.`, { status: c.status });
}

function requireStatus(c: ComplaintRow, allowed: ComplaintStatus[], action: string) {
  if (!allowed.includes(c.status)) invalid(c, action);
}

function requireRole(...roles: UserSummary['role'][]) {
  const viewer = me();
  if (!roles.includes(viewer.role)) throw new ApiError(403, 'FORBIDDEN', '이 기능을 쓸 권한이 없습니다.');
  return viewer;
}

/** 당사자만 조회 가능. 교사는 제출 이후만 (타 학교·타인 민원은 404) */
function load(id: string): ComplaintRow {
  const viewer = me();
  const c = complaints.get(id);
  const visible =
    c &&
    ((viewer.role === 'PARENT' && c.parentId === viewer.id) ||
      (viewer.role === 'TEACHER' && c.teacherId === viewer.id && !PRE_SUBMIT.includes(c.status)) ||
      viewer.role === 'RESPONSE_TEAM' ||
      viewer.role === 'ADMIN');
  if (!c || !visible) throw new ApiError(404, 'NOT_FOUND', '민원을 찾을 수 없습니다.');
  return c;
}

const latestCard = (c: ComplaintRow) => c.cards[c.cards.length - 1] ?? null;
/** 내부 기록을 빼고 응답 모양으로. 학부모 마음 상태(parentEmotion)는 교사·민원대응팀에게만 준다 */
const stripCard = (card: CardRow | null): SummaryCard | null => {
  if (!card) return null;
  const { source: _s, revisionInstruction: _r, suggestions: _g, parentEmotion, ...rest } = card;
  void _s, void _r, void _g;
  return clone(me().role === 'PARENT' ? rest : { ...rest, parentEmotion });
};

/** 역할별 응답 모양. 교사에게는 학부모 원문과 AI 설명을 빼고, 제출된 카드만 준다 (명세서 3.5, 7.7) */
function view(c: ComplaintRow): Complaint {
  const viewer = me();
  const isTeacher = viewer.role === 'TEACHER';
  const card = isTeacher ? c.cards.find((x) => x.version === c.submittedVersion) ?? null : latestCard(c);
  return {
    id: c.id,
    status: c.status,
    parent: clone(users[c.parentId]),
    teacher: c.teacherId ? clone(users[c.teacherId]) : null,
    student: clone(users[c.studentId]),
    roomId: c.roomId,
    // 교사에게는 학부모 원문·AI 상담 내용을 주지 않는다
    ...(isTeacher
      ? {}
      : {
          originalText: c.originalText,
          aiExplanation: clone(c.explanation),
          consultation: clone(c.consultation),
          consultStage: PRE_SUBMIT.includes(c.status) && c.status !== 'CARD_DRAFTING' ? c.consult.stage : null,
          proposedTeacher: c.consult.teacherId ? clone(users[c.consult.teacherId]) : null,
          category: c.consult.category,
        }),
    summaryCard: stripCard(card),
    teacherReply: clone(c.reply),
    escalation: clone(c.escalation),
    createdAt: c.createdAt,
    submittedAt: c.submittedAt,
    closedAt: c.closedAt,
  };
}

function cardResult(card: CardRow): SummaryCardResult {
  return { ...stripCard(card)!, ...(card.suggestions?.length ? { suggestions: clone(card.suggestions) } : {}) };
}

function pushCard(c: ComplaintRow, base: Omit<SummaryCard, 'version'>, source: CardSource, instruction?: string) {
  const card: CardRow = { ...base, version: c.cards.length + 1, source, revisionInstruction: instruction };
  card.suggestions = suggestionsFor(card);
  c.cards.push(card);
  emit({ event: 'complaint.card_updated', data: { complaintId: c.id, version: card.version } });
  return card;
}

function checkBase(c: ComplaintRow, baseVersion: number) {
  const latest = latestCard(c);
  if (!latest || latest.version !== baseVersion) {
    throw new ApiError(409, 'INVALID_COMPLAINT_STATUS', '카드가 그 사이 바뀌었습니다. 최신 카드를 다시 불러와 주세요.', { latestVersion: latest?.version });
  }
  return latest;
}

/** 상담사의 첫 반응. 목 AI 실패 재현: 민원 내용에 [AI실패]를 넣으면 한 번 422 */
function runFirstReply(c: ComplaintRow) {
  if (c.originalText.includes('[AI실패]') && !(c as { retried?: boolean }).retried) {
    (c as { retried?: boolean }).retried = true;
    throw new ApiError(422, 'AI_PROCESSING_FAILED', 'AI 응답이 늦어지고 있어요. 작성하신 내용은 저장되었으니 다시 시도해 주세요.', { complaintId: c.id });
  }
  c.consultation.push(...startConsult(c.originalText, c.consult, ctx, c.consult.category));
}

const parentMessage = (content: string, value?: string): ConsultMessage => ({
  id: genId('cm_'),
  from: 'PARENT',
  content,
  ...(value ? { value } : {}),
  createdAt: nowIso(),
});

export const mockComplaintApi: ComplaintApi = {
  async create(body, key) {
    const parent = requireRole('PARENT');
    await delay(AI_DELAY);
    const dupId = idempotency.get(`${parent.id}:${key}`);
    if (dupId) return view(complaints.get(dupId)!);

    // 방에서 시작하면 그 방의 선생님으로 정해지고, 채팅 목록에서 시작하면 제출할 때 고른다
    let roomId: string | null = null;
    let teacherId: string | null = null;
    if (body.roomId) {
      const room = rooms.get(body.roomId);
      const teacher = room && teacherOf(room);
      if (!room || !room.members.includes(parent.id) || !teacher) throw new ApiError(404, 'NOT_FOUND', '채팅방을 찾을 수 없습니다.');
      roomId = room.id;
      teacherId = teacher.id;
    }
    if (!parent.childStudentIds?.includes(body.studentId)) throw new ApiError(403, 'FORBIDDEN', '자녀로 등록된 학생이 아닙니다.');
    const content = checkContent(body.content);
    const TAGS = ['LIFE_GUIDANCE', 'STUDY_EVALUATION', 'PEER_RELATION', 'SAFETY', 'ETC'];
    if (body.category && !TAGS.includes(body.category)) {
      throw new ApiError(400, 'VALIDATION_ERROR', '분류를 다시 골라 주세요.', { fields: { category: 'invalid' } });
    }

    const c: ComplaintRow = {
      id: genId('cmp_'),
      roomId,
      parentId: parent.id,
      teacherId,
      studentId: body.studentId,
      status: 'EXPLAINED',
      originalText: content,
      explanation: null,
      consultation: [],
      consult: { ...emptyConsult(), category: body.category ?? null },
      cards: [],
      submittedVersion: null,
      reply: null,
      escalation: null,
      createdAt: nowIso(),
      submittedAt: null,
      acknowledgedAt: null,
      closedAt: null,
      resolvedAt: null,
    };
    c.consultation.push(parentMessage(content));
    complaints.set(c.id, c);
    idempotency.set(`${parent.id}:${key}`, c.id);
    emit({ event: 'complaint.status_changed', data: { complaintId: c.id, from: null, to: 'EXPLAINED', changedAt: c.createdAt } });
    runFirstReply(c); // 실패 시 원문은 보존된 채로 422
    return view(c);
  },

  async retryExplanation(id) {
    requireRole('PARENT');
    await delay(AI_DELAY);
    const c = load(id);
    requireStatus(c, ['EXPLAINED'], 'AI 답변을 다시 요청');
    if (c.consult.stage === null) runFirstReply(c);
    return view(c);
  },

  async consult(id, raw, value) {
    requireRole('PARENT');
    await delay(AI_DELAY - 400);
    const c = load(id);
    requireStatus(c, ['EXPLAINED'], '상담을 이어');
    if (c.consult.stage === null) throw new ApiError(422, 'AI_PROCESSING_FAILED', '첫 답변을 먼저 받아 주세요.', { complaintId: c.id });
    if (c.consultation.length >= MAX_TURNS) {
      throw new ApiError(400, 'VALIDATION_ERROR', "상담이 길어졌어요. '요약 카드 만들기'로 정리해 주세요.", { fields: { content: 'max turns' } });
    }
    const content = checkContent(raw);
    c.consultation.push(parentMessage(content, value));
    c.consultation.push(
      ...continueConsult(content, value, c.consult, ctx, {
        fixedTeacherId: c.teacherId,
        setExplanation: (e) => (c.explanation = e),
      }),
    );
    return view(c);
  },

  async listRecipients(id) {
    requireRole('PARENT');
    await delay();
    const c = load(id);
    if (c.teacherId) {
      const fixed = RECIPIENTS.find((r) => r.id === c.teacherId);
      return [{ teacher: clone(users[c.teacherId]), label: fixed?.label ?? '선생님', recommended: true, reason: '이 채팅방의 선생님이에요.' }];
    }
    const pick = c.consult.teacherId;
    return RECIPIENTS.map((r) => ({
      teacher: clone(users[r.id]),
      label: r.label,
      recommended: r.id === pick,
      ...(r.id === pick ? { reason: '상담에서 함께 정한 선생님이에요.' } : {}),
    }));
  },

  async close(id) {
    requireRole('PARENT');
    await delay();
    const c = load(id);
    requireStatus(c, ['EXPLAINED'], '종료');
    c.closedAt = nowIso();
    changeStatus(c, 'CLOSED_BY_EXPLANATION');
    return view(c);
  },

  async createSummaryCard(id) {
    requireRole('PARENT');
    await delay(AI_DELAY);
    const c = load(id);
    requireStatus(c, ['EXPLAINED'], '요약 카드를 생성');
    if (c.consult.stage !== 'READY') {
      throw new ApiError(409, 'INVALID_COMPLAINT_STATUS', '상담을 조금 더 나눈 뒤에 요약 카드를 만들 수 있어요.', { stage: c.consult.stage });
    }
    const card = pushCard(c, cardFromConsult(c.consult, users[c.studentId], c.createdAt), 'AI_GENERATED');
    changeStatus(c, 'CARD_DRAFTING');
    return cardResult(card);
  },

  async requestRevision(id, baseVersion, instruction) {
    requireRole('PARENT');
    await delay(AI_DELAY - 300);
    const c = load(id);
    requireStatus(c, ['CARD_DRAFTING'], '카드를 수정');
    const text = instruction.trim();
    if (!text) throw new ApiError(400, 'VALIDATION_ERROR', '수정할 내용을 적어 주세요.', { fields: { instruction: 'required' } });
    const base = checkBase(c, baseVersion);
    const revised = revise(base, text);
    if (!revised) {
      throw new ApiError(422, 'AI_PROCESSING_FAILED', "어떻게 고칠지 알아듣지 못했어요. 예: 요청 사항에 '보관 시간 단축 검토'도 넣어 주세요.");
    }
    return cardResult(pushCard(c, revised, 'AI_REVISION', text));
  },

  async editSummaryCard(id, baseVersion, fields: CardEditableFields) {
    requireRole('PARENT');
    await delay();
    const c = load(id);
    requireStatus(c, ['CARD_DRAFTING'], '카드를 수정');
    const base = clone(checkBase(c, baseVersion));
    const next: Omit<SummaryCard, 'version'> = {
      basicInfo: { ...base.basicInfo, tags: fields.tags ?? base.basicInfo.tags },
      attribution: base.attribution, // 학부모가 고칠 수 없음
      situation: (fields.situation ?? base.situation).map((s, i) => ({ ...s, order: i + 1 })),
      concerns: fields.concerns ?? base.concerns,
      requests: fields.requests ?? base.requests,
      preferredContact: fields.preferredContact ?? base.preferredContact,
      regulations: base.regulations, // 학부모가 고칠 수 없음
      urgency: base.urgency,
      parentEmotion: base.parentEmotion, // AI 판단 — 학부모가 고칠 수 없음
      confirmedByParentAt: null,
    };
    const errors: Record<string, string> = {};
    if (next.situation.length === 0 || next.situation.some((s) => !s.what.trim())) errors.situation = '상황을 한 가지 이상 적어 주세요.';
    if (next.requests.length === 0 || next.requests.some((r) => !r.trim())) errors.requests = '요청 사항을 한 가지 이상 적어 주세요.';
    if (next.preferredContact.methods.length === 0) errors.preferredContact = '연락 방법을 하나 이상 골라 주세요.';
    const tw = next.preferredContact.timeWindow;
    if (tw && tw.from >= tw.to) errors.preferredContact = '연락받을 시간의 시작이 끝보다 빨라야 해요.';
    if (Object.keys(errors).length) throw new ApiError(400, 'VALIDATION_ERROR', Object.values(errors)[0], { fields: errors });
    return cardResult(pushCard(c, next, 'PARENT_EDIT'));
  },

  async submit(id, cardVersion, teacherId, key) {
    const parent = requireRole('PARENT');
    await delay();
    const dup = idempotency.get(`${parent.id}:${key}`);
    if (dup) return view(complaints.get(dup)!);
    const c = load(id);
    requireStatus(c, ['CARD_DRAFTING'], '보내기');
    if (!RECIPIENTS.some((r) => r.id === teacherId) || (c.teacherId && c.teacherId !== teacherId)) {
      throw new ApiError(400, 'VALIDATION_ERROR', '받을 선생님을 다시 골라 주세요.', { fields: { teacherId: 'invalid' } });
    }
    const card = checkBase(c, cardVersion);
    // 받는 선생님과의 DIRECT 방에 연결 (없으면 새로 만든다)
    const room = c.roomId ? rooms.get(c.roomId)! : ensureDirect(parent.id, teacherId);
    c.roomId = room.id;
    c.teacherId = teacherId;
    const at = nowIso();
    card.confirmedByParentAt = at;
    c.submittedVersion = card.version;
    c.submittedAt = at;
    idempotency.set(`${parent.id}:${key}`, c.id);
    changeStatus(c, 'SUBMITTED');
    // 방에 COMPLAINT_CARD 1건 — 학부모와 교사가 같은 카드를 본다
    postMessage({
      id: genId('msg_'),
      roomId: room.id,
      type: 'COMPLAINT_CARD',
      sender: clone(parent),
      content: '민원 요약 카드를 보냈습니다.',
      attachments: [],
      complaintId: c.id,
      readByCounterpart: false,
      createdAt: at,
    });
    return view(c);
  },

  async list({ status, studentId } = {}) {
    await delay();
    const viewer = me();
    const items = [...complaints.values()]
      .filter((c) => {
        if (viewer.role === 'PARENT') return c.parentId === viewer.id;
        if (viewer.role === 'TEACHER') return c.teacherId === viewer.id && !PRE_SUBMIT.includes(c.status);
        return ['ESCALATED', 'RESOLVED'].includes(c.status) && !!c.escalation;
      })
      .filter((c) => !status?.length || status.includes(c.status))
      .filter((c) => !studentId || c.studentId === studentId)
      .sort((a, b) => toMs(b.createdAt) - toMs(a.createdAt))
      .map(view);
    return { items, nextCursor: null, hasMore: false };
  },

  async get(id) {
    await delay();
    return view(load(id));
  },

  async acknowledge(id) {
    requireRole('TEACHER');
    await delay();
    const c = load(id);
    requireStatus(c, ['SUBMITTED'], '열람 처리');
    c.acknowledgedAt = nowIso();
    changeStatus(c, 'TEACHER_REVIEWING');
    return view(c);
  },

  async createReplyDraft(id, body) {
    const teacher = requireRole('TEACHER');
    await delay(AI_DELAY - 300);
    const c = load(id);
    requireStatus(c, ['SUBMITTED', 'TEACHER_REVIEWING', 'IN_CONVERSATION'], '답변 초안을 생성');
    if (!body.situation.trim() && !body.reason.trim() && !body.nextActions.trim()) {
      throw new ApiError(400, 'VALIDATION_ERROR', '메모를 한 칸 이상 적어 주세요.', { fields: { situation: 'required' } });
    }
    return draftReply(body, users[c.studentId], teacher);
  },

  async reply(id, body) {
    const teacher = requireRole('TEACHER');
    await delay(300);
    const c = load(id);
    requireStatus(c, ['TEACHER_REVIEWING'], '답변');
    const errors: Record<string, string> = {};
    if (!body.content.trim()) errors.content = '보낼 내용을 적어 주세요.';
    if (body.content.length > 2000) errors.content = '2,000자까지 보낼 수 있어요.';
    if (body.contactMethod !== 'MESSAGE' && !body.scheduledAt) errors.scheduledAt = '안심전화·면담 시각을 정해 주세요.';
    if (Object.keys(errors).length) throw new ApiError(400, 'VALIDATION_ERROR', Object.values(errors)[0], { fields: errors });

    const sentAt = nowIso();
    const messageId = genId('msg_');
    c.reply = { content: body.content.trim(), contactMethod: body.contactMethod, scheduledAt: body.scheduledAt, sentAt };
    changeStatus(c, 'IN_CONVERSATION');
    postMessage({
      id: messageId,
      roomId: c.roomId!,
      type: 'COMPLAINT_REPLY',
      sender: clone(teacher),
      content: c.reply.content,
      attachments: [],
      complaintId: c.id,
      readByCounterpart: false,
      createdAt: sentAt,
    });
    const reservation = body.scheduledAt ? { reservationId: genId(body.contactMethod === 'CALL' ? 'sc_' : 'vs_'), scheduledAt: body.scheduledAt } : undefined;
    return {
      status: c.status,
      messageId,
      ...(body.contactMethod === 'CALL' ? { safeCall: reservation } : {}),
      ...(body.contactMethod === 'VISIT' ? { visit: reservation } : {}),
    };
  },

  async escalate(id, body) {
    requireRole('TEACHER');
    await delay(300);
    const c = load(id);
    if (c.escalation) throw new ApiError(409, 'INVALID_COMPLAINT_STATUS', '이미 민원대응팀에 도움을 요청했습니다.');
    requireStatus(c, ['SUBMITTED', 'TEACHER_REVIEWING', 'IN_CONVERSATION'], '도움 요청');
    const errors: Record<string, string> = {};
    if (body.reasonTypes.length === 0) errors.reasonTypes = '사유를 하나 이상 골라 주세요.';
    if (!body.description.trim()) errors.description = '상황 설명을 적어 주세요.';
    if (body.description.length > 2000) errors.description = '2,000자까지 쓸 수 있어요.';
    if (Object.keys(errors).length) throw new ApiError(400, 'VALIDATION_ERROR', Object.values(errors)[0], { fields: errors });
    const esc: Escalation = {
      id: genId('esc_'),
      reasonTypes: body.reasonTypes,
      description: body.description.trim(),
      stage: 'RESPONSE_TEAM',
      status: 'REQUESTED',
      assignee: null,
      requestedAt: nowIso(),
    };
    c.escalation = esc;
    changeStatus(c, 'ESCALATED');
    emit({ event: 'escalation.created', data: clone(esc) });
    return clone(esc);
  },

  async resolve(id) {
    requireRole('TEACHER', 'RESPONSE_TEAM', 'ADMIN');
    await delay();
    const c = load(id);
    requireStatus(c, ['IN_CONVERSATION', 'ESCALATED'], '처리 종료');
    c.resolvedAt = nowIso();
    changeStatus(c, 'RESOLVED');
    return view(c);
  },

  async listRegulations(q) {
    await delay();
    const k = q?.trim();
    return clone(REGULATIONS.filter((r) => !k || r.title.includes(k) || r.excerpt.includes(k)));
  },
};
