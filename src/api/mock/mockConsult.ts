// 목 AI 상담사: 한 번에 답을 내지 않고 여러 대화로 상담을 이어 간다.
//   공감 → 사실관계 묻기(일시·장소·있었던 일·아이 상태·바라는 점) → 규정·법령 설명
//   → 연락할 선생님 추론 → 연락 방법·시간 → 요약 카드 준비
// 말투는 상담사처럼: 감정을 먼저 알아주고, 한 번에 하나씩 열린 질문으로, 왜 묻는지 설명한다.
// 실제 서버에서는 LLM + 학교 공개 자료 검색이 이 역할을 한다.
import type {
  AiExplanation,
  ConsultFact,
  ConsultMessage,
  ComplaintTag,
  ConsultStage,
  ContactMethod,
  QuickReply,
  SummaryCard,
  TimeRange,
  UrgencyLevel,
  UserSummary,
} from '../types';
import { URGENCY_LABEL } from '../../lib/complaint';
import {
  assessEmotion,
  buildCard,
  CONCERN,
  detectUrgency,
  hasEmotion,
  parseMethods,
  parseTimeWindow,
  reg,
  REQUEST,
  rulesOf,
  sentencesOf,
  soften,
  urgencyReason,
} from './mockAi';

// ---------------- 상태 ----------------

type SlotKey = 'urgency' | 'when' | 'place' | 'detail' | 'impact' | 'request';

export interface ConsultState {
  stage: ConsultStage | null;
  /** 첫 화면에서 고른 사전 분류 */
  category: ComplaintTag | null;
  slots: Partial<Record<SlotKey, string>> & {
    methods?: ContactMethod[];
    timeWindow?: TimeRange | null;
    timeAnswered?: boolean;
    urgencyReason?: string | null;
  };
  /** 지금 답을 기다리는 질문 */
  pending: SlotKey | 'method' | 'time' | null;
  teacherId: string | null;
  /** 카드의 '상황'을 만들 원문 (최초 민원, 자세한 이야기, 덧붙인 말) */
  cardTexts: string[];
  /** 학부모가 직접 쓴 말 전체 (빠른 답 제외) — 감정 상태 판단용 */
  parentSaid: string[];
  /** 격한 감정을 한 번 알아주었는지 */
  emotionAcked: boolean;
}

export const emptyConsult = (): ConsultState => ({
  stage: null,
  category: null,
  slots: {},
  pending: null,
  teacherId: null,
  cardTexts: [],
  parentSaid: [],
  emotionAcked: false,
});

/** 학부모가 민원을 보낼 수 있는 선생님 (목 데이터: 담임 + 교과) */
export const RECIPIENTS: { id: string; label: string; subject?: RegExp; subjectName?: string }[] = [
  { id: 'usr_t1', label: '3학년 9반 담임 · 수학', subject: /수학/, subjectName: '수학' },
  { id: 'usr_t2', label: '3학년 영어', subject: /영어/, subjectName: '영어' },
];
export const HOMEROOM_ID = 'usr_t1';

interface Ctx {
  users: Record<string, UserSummary>;
  now: () => string;
  genId: (p: string) => string;
}

// ---------------- 말 뽑기 ----------------

const WHEN_RE = /((?:지난주|이번 ?주|지난달)\s*[월화수목금]요일|\d{1,2}월 ?\d{1,2}일\s*\d교시|오늘|어제|그저께|그제|지난주|이번 ?주|지난달|\d{1,2}월 ?\d{1,2}일|[월화수목금]요일|\d교시|아침|점심시간|방과 ?후|등교|하교)/;
const PLACE_RE = /(수학|영어|국어|과학|사회|체육관|체육(?!복|관)|음악|미술|조회|종례|담임 ?시간|쉬는 ?시간|점심시간|교실|운동장|급식실|복도|체육관|야간 ?자율학습|야자|자율학습|학원)/;
const SUBJECT_RE = /^(수학|영어|국어|과학|사회|체육|음악|미술)$/;
const IMPACT_RE = new RegExp(`${CONCERN.source}|괜찮아|싫어해|싫다고|무서워|울었|울면서|울어|당황|기운이 없`);
/** '설명해 주지 않았대요'처럼 있었던 일을 말하는 문장은 요청으로 보지 않는다 */
const REQ_RE = new RegExp(`${REQUEST.source}|사과를|재발|싶어요|싶습니다`);

/** 예: '지난주 금요일' + '3교시' → '지난주 금요일 3교시' */
function merge(old: string | undefined, next: string | undefined, addOn: RegExp) {
  if (!next || next === old) return old;
  if (!old || old === '확인 필요') return next;
  return addOn.test(next) && !addOn.test(old) ? `${old} ${next}` : next;
}

const sentenceWith = (text: string, re: RegExp) => sentencesOf(text).find((x) => re.test(x));

/** 자유롭게 쓴 말에서 아직 비어 있는 칸을 채운다 */
function extract(text: string, cs: ConsultState) {
  const s = cs.slots;
  const when = text.match(WHEN_RE)?.[1];
  if (when && !s.when) s.when = when;
  const place = text.match(PLACE_RE)?.[1];
  if (place && !s.place) s.place = SUBJECT_RE.test(place) ? `${place} 수업` : place;
  const impact = sentenceWith(text, IMPACT_RE);
  if (impact && !s.impact) s.impact = soften(impact);
  const request = sentenceWith(text, REQ_RE);
  if (request && !s.request) s.request = soften(request);
  const methods = parseMethods(text);
  if (methods && /연락|전화|통화|문자|면담|방문/.test(text) && !s.methods) s.methods = methods;
  const tw = parseTimeWindow(text);
  if (tw && !s.timeAnswered) {
    s.timeWindow = tw;
    s.timeAnswered = true;
  }
}

// ---------------- 질문 ----------------

const URGENCY_QUICK: QuickReply[] = [
  { label: '오늘 안에 꼭 확인이 필요해요', value: 'URGENT' },
  { label: '며칠 안에 답을 받으면 돼요', value: 'SOON' },
  { label: '급하지 않아요', value: 'NORMAL' },
];

const QUESTIONS: Record<SlotKey, { content: string; note: string; quick?: QuickReply[] }> = {
  urgency: {
    content: '먼저 하나 여쭤볼게요. 이 일은 선생님이 얼마나 빨리 확인해 주시면 좋을까요?',
    note: "아이의 안전이나 건강이 걸린 일이면 '오늘 안에'를 골라 주세요. 카드에 '긴급'으로 표시해 선생님께 먼저 알려 드려요.",
    quick: URGENCY_QUICK,
  },
  when: {
    content: '괜찮으시다면, 그 일이 언제 있었는지 여쭤봐도 될까요?',
    note: '날짜나 교시를 알면 선생님이 당시 기록을 찾아 사실을 확인하기 쉬워요.',
    quick: [{ label: '오늘' }, { label: '어제' }, { label: '이번 주' }, { label: '지난주' }, { label: '잘 기억나지 않아요', value: 'UNKNOWN' }],
  },
  place: {
    content: '그때가 어느 수업 시간이었는지, 혹은 어디에서 있었던 일인지 기억나세요?',
    note: '수업 중이었다면 그 수업을 맡은 선생님이 상황을 가장 잘 알고 계세요.',
    quick: [{ label: '조회·종례 시간' }, { label: '수학 수업' }, { label: '영어 수업' }, { label: '쉬는 시간' }, { label: '잘 모르겠어요', value: 'UNKNOWN' }],
  },
  detail: {
    content: '아이에게 들으신 이야기를 조금 더 들려주실 수 있을까요? 천천히 말씀하셔도 괜찮아요.',
    note: '선생님이 어떤 말이나 조치를 했는지, 아이는 어떻게 반응했는지가 사실관계를 확인하는 데 중요해요.',
    quick: [{ label: '아이에게 더 들은 건 없어요', value: 'UNKNOWN' }],
  },
  impact: {
    content: '이 일이 있고 나서 아이는 좀 어떻게 지내고 있나요?',
    note: '아이의 지금 마음 상태도 선생님께 함께 전해 드리면 아이를 살피는 데 도움이 돼요.',
    quick: [{ label: '많이 속상해해요' }, { label: '학교 가기 싫어해요' }, { label: '겉으로는 괜찮아 보여요' }],
  },
  request: {
    content: '보호자님께서는 학교가 어떻게 해 주면 마음이 좀 놓이실 것 같으세요?',
    note: '바라시는 점이 분명하면 선생님도 무엇을 준비해서 답해야 할지 알 수 있어요.',
    quick: [{ label: '사실 확인과 설명' }, { label: '재발 방지 약속' }, { label: '아이에 대한 관심과 지도' }, { label: '일단 상황만 알리고 싶어요' }],
  },
};
const ORDER: SlotKey[] = ['urgency', 'when', 'place', 'detail', 'impact', 'request'];

/** 빠른 답 문구 (감정 판단에서 제외) */
const QUICK_LABELS = new Set(Object.values(QUESTIONS).flatMap((q) => q.quick?.map((x) => x.label) ?? []));

const REQUEST_LABEL: Record<string, string> = {
  '사실 확인과 설명': '사실관계 확인과 설명',
  '재발 방지 약속': '같은 일이 반복되지 않도록 방안 안내',
  '아이에 대한 관심과 지도': '학생에 대한 관심과 지도',
  '일단 상황만 알리고 싶어요': '상황 공유 (별도 조치 요청 없음)',
};

/** 받침에 따라 '으로' / '로' (ㄹ 받침은 '로') */
function euro(word: string) {
  const code = word.charCodeAt(word.length - 1) - 0xac00;
  if (code < 0 || code > 11171) return '(으)로';
  const jong = code % 28;
  return jong === 0 || jong === 8 ? '로' : '으로';
}

const METHOD_LABEL: Record<ContactMethod, string> = { MESSAGE: '채팅 메시지', CALL: '안심전화', VISIT: '방문 면담' };

// ---------------- 메시지 만들기 ----------------

function ai(ctx: Ctx, m: Omit<ConsultMessage, 'id' | 'from' | 'createdAt'>): ConsultMessage {
  return { id: ctx.genId('cm_'), from: 'AI', createdAt: ctx.now(), ...m };
}

function ask(ctx: Ctx, cs: ConsultState, key: SlotKey, lead?: string): ConsultMessage {
  cs.pending = key;
  const q = QUESTIONS[key];
  return ai(ctx, { content: lead ? `${lead}\n${q.content}` : q.content, note: q.note, quickReplies: q.quick });
}

const nextEmpty = (cs: ConsultState) => ORDER.find((k) => !cs.slots[k]);

function factsOf(cs: ConsultState, ctx: Ctx, withContact = false): ConsultFact[] {
  const s = cs.slots;
  // 처음 말한 일과 덧붙인 이야기에서 첫 문장씩
  const what = cs.cardTexts
    .map((t) => soften(sentencesOf(t)[0] ?? t))
    .filter(Boolean)
    .join(' ');
  const facts: ConsultFact[] = [
    { label: '대응 시급도', value: s.urgency ? URGENCY_LABEL[s.urgency as UrgencyLevel] : '확인 필요' },
    { label: '일시', value: s.when ?? '확인 필요' },
    { label: '장소·수업', value: s.place ?? '확인 필요' },
    { label: '있었던 일', value: what.length > 90 ? `${what.slice(0, 90)}…` : what },
    { label: '아이 상태', value: s.impact ?? '—' },
    { label: '바라는 점', value: s.request ?? '—' },
  ];
  if (withContact) {
    if (cs.teacherId) facts.push({ label: '전할 선생님', value: `${ctx.users[cs.teacherId].name} 선생님` });
    facts.push({
      label: '희망 연락',
      value: [s.methods?.map((m) => METHOD_LABEL[m]).join('·') ?? '채팅 메시지', s.timeWindow ? `${s.timeWindow.from}~${s.timeWindow.to}` : '시간 무관'].join(', '),
    });
    // 학부모 마음 상태는 교사용 카드에만 담기므로 학부모에게 보이는 정리표에는 넣지 않는다
  }
  return facts;
}

/** 걸린 규칙을 모두 모아 규정·법령 설명 하나로 */
export function mergedExplanation(texts: string[], category?: ComplaintTag | null): AiExplanation {
  const rules = rulesOf(texts, category);
  return {
    empathy: rules[0].empathy,
    reason: rules.map((r) => r.reason).join(' '),
    alternative: rules.map((r) => r.alternative).join(' '),
    citations: [...new Set(rules.flatMap((r) => r.regulations))].map(reg),
    recommendation: rules.some((r) => r.recommendation === 'TALK_TO_TEACHER') ? 'TALK_TO_TEACHER' : 'EXPLAINED',
  };
}

const ACTIONS_AFTER_EXPLAIN: QuickReply[] = [
  { label: '선생님께 전하고 싶어요', value: 'TO_TEACHER' },
  { label: '설명으로 충분해요', value: 'CLOSE' },
  { label: '정리한 내용을 고치고 싶어요', value: 'MORE' },
];

function explainStage(ctx: Ctx, cs: ConsultState, setExplanation: (e: AiExplanation) => void): ConsultMessage[] {
  cs.stage = 'EXPLAIN';
  cs.pending = null;
  const explanation = mergedExplanation(cs.cardTexts, cs.category);
  setExplanation(explanation);
  return [
    ai(ctx, {
      content: '차근차근 말씀해 주셔서 고마워요. 들려주신 이야기를 사실관계로 정리해 봤어요.',
      note: '제가 잘못 이해한 부분이 있으면 편하게 고쳐 주세요.',
      facts: factsOf(cs, ctx),
    }),
    ai(ctx, { content: '그리고 이 일과 관련된 학교 규정과 법령을 찾아봤어요.', explanation }),
    ai(ctx, {
      content:
        '설명을 들으시고 마음이 좀 어떠세요?\n궁금하셨던 점이 풀리셨다면 여기서 마무리해도 괜찮아요. 그래도 선생님과 직접 이야기 나누고 싶으시면, 누구에게 전하면 좋을지 같이 정해 볼게요.',
      quickReplies: ACTIONS_AFTER_EXPLAIN,
    }),
  ];
}

/** 누구에게 연락할지: 상담 내용과 규정의 소관을 근거로 추론한다 */
function recipientReasoning(cs: ConsultState, ctx: Ctx) {
  const texts = [...cs.cardTexts, cs.slots.place ?? ''];
  const joined = texts.join(' ');
  const rules = rulesOf(cs.cardTexts, cs.category);
  const tags = new Set(rules.map((r) => r.tag));
  const subject = RECIPIENTS.find((r) => r.id !== HOMEROOM_ID && r.subject?.test(joined));
  const homeroom = ctx.users[HOMEROOM_ID].name;
  const bullets: string[] = [];

  if (subject) bullets.push(`${subject.subjectName} 시간에 있었던 일이라, 수업을 맡은 ${ctx.users[subject.id].name} 선생님이 당시 상황을 가장 잘 알고 계세요.`);
  if (tags.has('LIFE_GUIDANCE')) bullets.push(`휴대전화 보관·복장·출결 같은 생활지도는 담임 ${homeroom} 선생님이 맡고 계세요.`);
  if (tags.has('PEER_RELATION') || tags.has('SAFETY')) bullets.push(`친구 관계나 안전에 관한 일은 담임 ${homeroom} 선생님이 먼저 살피고, 필요하면 학교 차원에서 함께 확인해요.`);
  if (tags.has('STUDY_EVALUATION') && !subject) bullets.push('평가에 관한 일은 그 과목 선생님이 채점 기준을 알고 계세요. 어느 과목인지 알려 주시면 더 정확히 찾아 드릴게요.');
  if (rules.length > 1) bullets.push('여러 일이 함께 있어서, 담임선생님이 먼저 전체를 보시고 필요하면 교과 선생님과 나누시는 게 빨라요.');
  if (bullets.length === 0) bullets.push(`아이의 학교생활 전반은 담임 ${homeroom} 선생님이 가장 잘 알고 계세요.`);

  const studyOnly = rules.every((r) => r.tag === 'STUDY_EVALUATION');
  const pick = subject && studyOnly && rules.length === 1 ? subject.id : HOMEROOM_ID;
  if (subject && pick === HOMEROOM_ID && tags.has('LIFE_GUIDANCE')) {
    bullets.push(`이번 일의 중심은 생활지도라서, 담임 ${homeroom} 선생님께 먼저 전하시고 필요하면 ${ctx.users[subject.id].name} 선생님과 함께 확인하시는 게 좋겠어요.`);
  }
  if (pick === subject?.id) bullets.push(`채점 기준과 감점 사유는 ${ctx.users[subject.id].name} 선생님이 직접 설명해 주실 수 있어요.`);
  return { bullets, pick };
}

function recipientQuick(pick: string, ctx: Ctx): QuickReply[] {
  return [...RECIPIENTS]
    .sort((a, b) => Number(b.id === pick) - Number(a.id === pick))
    .map((r) => ({ label: `${ctx.users[r.id].name} 선생님${r.id === pick ? ' (추천)' : ''}`, value: r.id }));
}

function recipientStage(ctx: Ctx, cs: ConsultState, fixedTeacherId: string | null): ConsultMessage[] {
  if (fixedTeacherId) {
    cs.teacherId = fixedTeacherId;
    return contactStage(ctx, cs, `${ctx.users[fixedTeacherId].name} 선생님께 전해 드릴게요.`);
  }
  cs.stage = 'RECIPIENT';
  cs.pending = null;
  const { bullets, pick } = recipientReasoning(cs, ctx);
  return [
    ai(ctx, { content: '그럼 이 이야기를 누구에게 전하면 좋을지 같이 생각해 볼게요.', reasoning: bullets }),
    ai(ctx, {
      content: `제 생각에는 ${ctx.users[pick].name} 선생님께 먼저 전하시는 게 좋겠어요. 보호자님 생각은 어떠세요?`,
      note: '다른 선생님이 더 편하시면 그분을 골라 주셔도 괜찮아요.',
      quickReplies: recipientQuick(pick, ctx),
    }),
  ];
}

function contactStage(ctx: Ctx, cs: ConsultState, lead: string): ConsultMessage[] {
  cs.stage = 'CONTACT';
  if (!cs.slots.methods) {
    cs.pending = 'method';
    return [
      ai(ctx, {
        content: `${lead}\n선생님이 답을 드릴 때 어떤 방법이 편하세요?`,
        note: '안심전화는 개인 번호를 드러내지 않고 통화할 수 있어요.',
        quickReplies: [
          { label: '채팅 메시지', value: 'MESSAGE' },
          { label: '안심전화', value: 'CALL' },
          { label: '방문 면담', value: 'VISIT' },
        ],
      }),
    ];
  }
  if (!cs.slots.timeAnswered) return [askTime(ctx, cs, lead)];
  return readyStage(ctx, cs, lead);
}

function askTime(ctx: Ctx, cs: ConsultState, lead?: string): ConsultMessage {
  cs.pending = 'time';
  return ai(ctx, {
    content: `${lead ? `${lead}\n` : ''}연락받기 편한 시간대가 있으실까요?`,
    note: '선생님의 대화 가능 시간 안에서 맞춰 드릴게요.',
    quickReplies: [
      { label: '오전 9~12시', value: '09:00-12:00' },
      { label: '점심 12~13시', value: '12:00-13:00' },
      { label: '오후 4~5시', value: '16:00-17:00' },
      { label: '언제든 괜찮아요', value: 'ANY' },
    ],
  });
}

function readyStage(ctx: Ctx, cs: ConsultState, lead?: string): ConsultMessage[] {
  cs.stage = 'READY';
  cs.pending = null;
  return [
    ai(ctx, {
      content: `${lead ? `${lead}\n` : ''}오늘 이야기 나눠 주셔서 정말 고마워요. 지금까지 함께 정리한 내용이에요.`,
      facts: factsOf(cs, ctx, true),
    }),
    ai(ctx, {
      content: '이 내용으로 선생님께 드릴 요약 카드를 만들어 볼까요?',
      note: '카드에는 감정 표현은 덜고 있었던 일과 바라는 점 위주로 담겨요. 보내기 전에 보호자님이 확인하고 고치실 수 있어요.',
      quickReplies: [{ label: '요약 카드 만들기', value: 'MAKE_CARD' }],
    }),
  ];
}

// ---------------- 진입점 ----------------

/** 첫 민원에 대한 상담사의 첫 반응: 공감 → 상담 방식 안내 → 첫 질문 */
export function startConsult(text: string, cs: ConsultState, ctx: Ctx, category?: ComplaintTag | null): ConsultMessage[] {
  cs.stage = 'FACTS';
  cs.category = category ?? null;
  cs.cardTexts = [text];
  cs.parentSaid = [text];
  extract(text, cs);
  if (sentencesOf(text).length >= 3) cs.slots.detail = 'given';

  const rule = rulesOf([text], cs.category)[0];
  const agitated = assessEmotion([text]).level === 'AGITATED';
  cs.emotionAcked = agitated;
  const feelings = agitated || hasEmotion(text) ? '그런 일을 겪으시면 화가 나고 답답하신 게 당연해요. ' : '';
  // 빠른 대응 여부는 언제나 가장 먼저 확인한다 (안전·건강 표현이 보이면 그 점을 짚는다)
  const hint = detectUrgency(text) === 'URGENT';
  const lead = hint
    ? '말씀을 들어 보니 서둘러 살펴야 할 일일 수도 있겠어요. 보호자님 이야기를 제대로 이해하고 도와드릴 수 있도록, 몇 가지만 하나씩 여쭤볼게요.'
    : '보호자님 이야기를 제대로 이해하고 도와드릴 수 있도록, 몇 가지만 하나씩 여쭤볼게요.';
  return [
    ai(ctx, {
      content: `${rule.empathy}\n${feelings}이렇게 먼저 이야기를 꺼내 주셔서 고마워요.`,
    }),
    ask(ctx, cs, nextEmpty(cs) ?? 'request', lead),
  ];
}

const URGENCY_ACK: Record<UrgencyLevel, string> = {
  URGENT:
    "알겠어요. 카드에 '긴급'으로 표시해서 선생님이 가장 먼저 보시도록 할게요.\n혹시 지금 아이가 위험한 상황이라면 기다리지 마시고 112·119나 학교 교무실로 바로 연락해 주세요.",
  SOON: '네, 며칠 안에 답을 받으실 수 있도록 전해 둘게요.',
  NORMAL: '네, 차분히 확인해도 되는 일로 전해 둘게요.',
};

function parseUrgency(text: string, value?: string): UrgencyLevel {
  if (value === 'URGENT' || value === 'SOON' || value === 'NORMAL') return value;
  if (/급하지 않|괜찮|천천히|여유/.test(text)) return 'NORMAL';
  return detectUrgency(text) ?? 'SOON';
}

/** 학부모의 답 하나에 상담사의 다음 말(들) */
export function continueConsult(
  text: string,
  value: string | undefined,
  cs: ConsultState,
  ctx: Ctx,
  opts: { fixedTeacherId: string | null; setExplanation: (e: AiExplanation) => void },
): ConsultMessage[] {
  const s = cs.slots;

  // 직접 쓴 말만 감정 판단에 쓴다. 처음으로 격한 표현이 나오면 그 마음부터 알아준다
  let feel = '';
  if (!value && !QUICK_LABELS.has(text)) {
    cs.parentSaid.push(text);
    if (!cs.emotionAcked && assessEmotion([text]).level === 'AGITATED') {
      cs.emotionAcked = true;
      feel = '많이 화가 나고 답답하신 마음, 충분히 이해해요. 천천히 하나씩 함께 정리해 볼게요.';
    }
  }
  const withFeel = (out: ConsultMessage[]) => (feel ? [ai(ctx, { content: feel }), ...out] : out);

  return withFeel(step());

  function step(): ConsultMessage[] {
  switch (cs.stage) {
    case 'FACTS': {
      const key = cs.pending;
      let ack = '';
      if (key === 'urgency') {
        const level = parseUrgency(text, value);
        s.urgency = level;
        s.urgencyReason = level === 'URGENT' ? urgencyReason(cs.parentSaid.join(' ')) ?? '보호자가 빠른 확인 요청' : null;
        ack = URGENCY_ACK[level];
      } else if (key && key !== 'method' && key !== 'time') {
        if (value === 'UNKNOWN') {
          s[key] = key === 'detail' ? 'given' : '확인 필요';
          ack = '괜찮아요. 선생님이 확인하실 수 있게 그렇게 적어 둘게요.';
        } else if (key === 'detail') {
          s.detail = 'given';
          cs.cardTexts.push(text);
          extract(text, cs);
          ack = CONCERN.test(text) || hasEmotion(text)
            ? '이야기를 들으니 아이도, 보호자님도 많이 놀라고 속상하셨을 것 같아요. 자세히 들려주셔서 고마워요.'
            : '자세히 들려주셔서 고마워요. 그때 상황이 조금 더 그려져요.';
        } else {
          // 빠른 답은 그대로 칸에 넣고, 자유 입력은 다른 칸도 함께 찾아 채운다
          const quick = QUESTIONS[key].quick?.some((q) => q.label === text);
          if (quick) s[key] = key === 'request' ? REQUEST_LABEL[text] ?? text : text;
          else extract(text, cs);
          if (key === 'when') s.when = s.when ?? text.trim().slice(0, 30);
          if (key === 'place') s.place = s.place ?? text.trim().slice(0, 30);
          if (key === 'impact') s.impact = s.impact ?? soften(text);
          if (key === 'request') s.request = s.request ?? soften(text);
          ack =
            key === 'when'
              ? `${s.when} 있었던 일이군요.`
              : key === 'place'
                ? /수업|시간|조회|종례|자율학습|야자/.test(s.place!)
                  ? `${s.place} 때였군요. 말씀해 주셔서 고마워요.`
                  : `${s.place}에서 있었던 일이군요. 말씀해 주셔서 고마워요.`
                : key === 'impact'
                  ? /괜찮/.test(text)
                    ? '겉으로라도 괜찮아 보인다니 다행이에요. 그래도 보호자님 마음 한편은 계속 쓰이시죠.'
                    : '아이가 그렇게 지내고 있다니, 곁에서 지켜보시는 보호자님 마음도 많이 무거우시겠어요.'
                  : '네, 바라시는 점을 분명히 담아 둘게요.';
        }
      }
      const next = nextEmpty(cs);
      if (next) return [ask(ctx, cs, next, ack)];
      const out = explainStage(ctx, cs, opts.setExplanation);
      if (ack) out.unshift(ai(ctx, { content: ack }));
      return out;
    }

    case 'EXPLAIN': {
      if (value === 'TO_TEACHER') return recipientStage(ctx, cs, opts.fixedTeacherId);
      if (value === 'MORE') {
        cs.pending = 'detail';
        return [ai(ctx, { content: '물론이에요. 고치거나 더하고 싶은 내용을 편하게 적어 주세요. 바로 정리에 반영할게요.' })];
      }
      // 덧붙인 말 → 정리에 반영하고 다시 묻는다 (새로 말한 일시·장소는 기존 값과 합치거나 바꾼다)
      cs.cardTexts.push(text);
      const before = { ...s };
      Object.assign(s, { when: undefined, place: undefined, request: undefined });
      extract(text, cs);
      s.when = merge(before.when, s.when, /교시/);
      s.place = s.place ?? before.place;
      s.request = before.request && s.request && !before.request.includes(s.request) ? `${before.request} / ${s.request}` : s.request ?? before.request;
      const explanation = mergedExplanation(cs.cardTexts, cs.category);
      opts.setExplanation(explanation);
      return [
        ai(ctx, { content: '말씀해 주셔서 고마워요. 정리에 반영했어요.', facts: factsOf(cs, ctx) }),
        ai(ctx, { content: '이렇게 정리하면 될까요? 다음으로 어떻게 하고 싶으세요?', quickReplies: ACTIONS_AFTER_EXPLAIN }),
      ];
    }

    case 'RECIPIENT': {
      const byValue = RECIPIENTS.find((r) => r.id === value);
      const byText =
        RECIPIENTS.find((r) => text.includes(ctx.users[r.id].name)) ??
        (/담임/.test(text) ? RECIPIENTS.find((r) => r.id === HOMEROOM_ID) : RECIPIENTS.find((r) => r.subject?.test(text)));
      const chosen = byValue ?? byText;
      if (!chosen) {
        const { pick } = recipientReasoning(cs, ctx);
        return [ai(ctx, { content: '어느 선생님께 전하면 좋을지, 아래에서 한 분을 골라 주시겠어요?', quickReplies: recipientQuick(pick, ctx) })];
      }
      cs.teacherId = chosen.id;
      return contactStage(ctx, cs, `좋아요, ${ctx.users[chosen.id].name} 선생님께 전해 드릴게요.`);
    }

    case 'CONTACT': {
      if (cs.pending === 'method') {
        const m = (['MESSAGE', 'CALL', 'VISIT'] as ContactMethod[]).find((x) => x === value);
        s.methods = m ? [m] : parseMethods(text) ?? ['MESSAGE'];
        const tw = parseTimeWindow(text);
        if (tw) Object.assign(s, { timeWindow: tw, timeAnswered: true });
        const label = s.methods.map((x) => METHOD_LABEL[x]).join('·');
        const lead = `${label}${euro(label)} 연락받으시는 걸로 적어 둘게요.`;
        return s.timeAnswered ? readyStage(ctx, cs, lead) : [askTime(ctx, cs, lead)];
      }
      if (value === 'ANY' || /상관없|언제든/.test(text)) s.timeWindow = null;
      else if (value && /^\d{2}:\d{2}-\d{2}:\d{2}$/.test(value)) {
        const [from, to] = value.split('-');
        s.timeWindow = { from, to };
      } else s.timeWindow = parseTimeWindow(text);
      s.timeAnswered = true;
      return readyStage(ctx, cs, s.timeWindow ? `${s.timeWindow.from}~${s.timeWindow.to} 사이로 전해 둘게요.` : '시간은 선생님 편한 때로 전해 둘게요.');
    }

    case 'READY':
    default: {
      cs.cardTexts.push(text);
      extract(text, cs);
      return [
        ai(ctx, {
          content: '그 내용도 카드에 함께 담을게요. 더 하실 말씀이 없으면 요약 카드를 만들어 볼게요.',
          quickReplies: [{ label: '요약 카드 만들기', value: 'MAKE_CARD' }],
        }),
      ];
    }
  }
  }
}

/** 상담 전체를 한 장의 카드로: 원문 기반 카드에 상담에서 확인한 칸을 덧입힌다 */
export function cardFromConsult(cs: ConsultState, student: UserSummary, receivedAt: string): Omit<SummaryCard, 'version'> {
  const base = buildCard(cs.cardTexts, student, receivedAt, cs.category);
  const s = cs.slots;
  const known = (v?: string) => v && v !== '확인 필요' && v !== 'given';
  // 아이 상태·바라는 점으로 옮긴 문장은 상황에서 뺀다
  const wants = (s.request ?? '').split(' / ').filter(Boolean);
  const moved = new Set([s.impact, ...wants].filter(Boolean));
  const situation = base.situation
    .filter((x) => !moved.has(x.what))
    .map((x, i) => ({
      ...x,
      order: i + 1,
      ...(i === 0 ? { when: known(s.when) ? s.when! : x.when, where: known(s.place) ? s.place! : x.where } : {}),
    }));
  const concerns = [...base.concerns];
  if (s.impact && !/괜찮/.test(s.impact) && !concerns.includes(s.impact)) concerns.unshift(s.impact);
  const requests = base.requests.filter((r) => !wants.includes(r));
  requests.unshift(...wants);
  return {
    ...base,
    situation,
    concerns: concerns.slice(0, 5),
    requests: requests.slice(0, 6),
    preferredContact: {
      methods: s.methods ?? base.preferredContact.methods,
      timeWindow: s.timeAnswered ? s.timeWindow ?? null : base.preferredContact.timeWindow,
    },
    urgency: { level: (s.urgency as UrgencyLevel | undefined) ?? base.urgency.level, reason: s.urgencyReason ?? base.urgency.reason },
    // 상담 전체에서 학부모가 직접 쓴 말로 판단 (빠른 답 제외)
    parentEmotion: assessEmotion(cs.parentSaid),
  };
}
