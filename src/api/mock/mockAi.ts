// 목 AI: 키워드 규칙으로 명세서의 AI 동작(규정 설명 · 요약 카드 · 수정 · 순화 · 입장 정리)을 흉내 낸다.
// 실제 서버에서는 학교 공개 자료(/regulations) 검색 + LLM이 이 역할을 한다.
import type {
  AiExplanation,
  CardSuggestion,
  ComplaintTag,
  ContactMethod,
  Regulation,
  ReplyDraft,
  ReplyDraftBody,
  SituationItem,
  SummaryCard,
  TimeRange,
  UserSummary,
} from '../types';

export const REGULATIONS: Regulation[] = [
  {
    id: 'reg_12',
    sourceType: 'SCHOOL_RULE',
    title: '학생생활규정 제12조(휴대전화)',
    excerpt: '수업 시간 중 휴대전화는 담임교사가 일괄 보관하며, 종례 시 돌려준다. 보호자의 긴급한 연락은 교무실 대표번호로 한다.',
    documentUrl: '#reg_12',
  },
  {
    id: 'reg_8',
    sourceType: 'SCHOOL_RULE',
    title: '학생생활규정 제8조(용의·복장)',
    excerpt: '학생은 교복을 단정히 착용하며, 체육 활동 시에는 체육복을 입는다. 계절·활동별 착용 기준은 학교장이 따로 정한다.',
    documentUrl: '#reg_8',
  },
  {
    id: 'reg_15',
    sourceType: 'SCHOOL_RULE',
    title: '학업성적관리규정 제15조(수행평가)',
    excerpt: '수행평가의 평가 기준과 채점 기준은 학기 초에 공개하며, 학생은 결과 확인 후 7일 이내에 이의를 신청할 수 있다.',
    documentUrl: '#reg_15',
  },
  {
    id: 'reg_9',
    sourceType: 'SCHOOL_RULE',
    title: '학업성적관리규정 제9조(출결)',
    excerpt: '질병·경조사 등으로 결석한 경우 결석 후 5일 이내에 증빙 서류를 담임교사에게 제출한다.',
    documentUrl: '#reg_9',
  },
  {
    id: 'reg_n87',
    sourceType: 'NOTICE_LETTER',
    title: '가정통신문 제2026-87호(야간 자율학습 운영 안내)',
    excerpt: '야간 자율학습에 빠질 때는 보호자가 미리 담임교사에게 알려 주세요. 사전 연락이 없으면 보호자께 문자로 안내합니다.',
    documentUrl: '#reg_n87',
  },
  {
    id: 'reg_law_safety',
    sourceType: 'LAW',
    title: '학교안전사고 예방 및 보상에 관한 법률',
    excerpt: '교육활동 중 일어난 학교안전사고는 학교안전공제회에 보상을 청구할 수 있다.',
    documentUrl: '#reg_law_safety',
  },
];

const reg = (id: string) => REGULATIONS.find((r) => r.id === id)!;

interface Rule {
  match: RegExp;
  tag: ComplaintTag;
  regulations: string[];
  recommendation: AiExplanation['recommendation'];
  empathy: string;
  reason: string;
  alternative: string;
  situation: Omit<SituationItem, 'order'>[];
  concerns: string[];
  requests: string[];
}

const RULES: Rule[] = [
  {
    match: /휴대폰|핸드폰|휴대전화|스마트폰/,
    tag: 'LIFE_GUIDANCE',
    regulations: ['reg_12'],
    recommendation: 'EXPLAINED',
    empathy: '아이와 연락이 닿지 않아 많이 걱정되셨겠어요.',
    reason: '학교 학생생활규정에 따라 수업 시간 중 휴대전화는 담임선생님이 보관하고 종례 때 돌려줍니다.',
    alternative: '급한 연락은 교무실 대표번호로 해 주시면 학생에게 바로 전달해 드려요.',
    situation: [{ when: '수업 시간 중', where: '교실', what: '학생의 휴대전화를 보관함' }],
    concerns: ['급한 상황에 학생과 연락할 방법이 없음'],
    requests: ['휴대전화 보관 중 보호자 긴급 연락 방법 안내'],
  },
  {
    match: /수행평가|점수|성적|채점|시험|평가/,
    tag: 'STUDY_EVALUATION',
    regulations: ['reg_15'],
    recommendation: 'TALK_TO_TEACHER',
    empathy: '아이가 열심히 준비한 만큼 결과가 아쉬우셨을 것 같아요.',
    reason: '학업성적관리규정에 따라 수행평가 채점 기준은 학기 초에 공개되고, 결과를 확인한 뒤 7일 안에 이의를 신청할 수 있어요. 구체적인 채점 내용은 담당 선생님이 확인해 주셔야 해요.',
    alternative: '채점 기준표는 학교 홈페이지 공지에서 볼 수 있어요.',
    situation: [{ when: '최근', where: '—', what: '수행평가 결과를 받음' }],
    concerns: ['평가 결과가 학생이 준비한 내용과 다르게 반영되었을 수 있음'],
    requests: ['채점 기준과 감점 사유 설명'],
  },
  {
    match: /야자|야간 ?자율|자율학습/,
    tag: 'LIFE_GUIDANCE',
    regulations: ['reg_n87'],
    recommendation: 'EXPLAINED',
    empathy: '갑자기 연락을 받으셔서 놀라셨겠어요.',
    reason: '야간 자율학습 운영 안내에 따라, 미리 알리지 않고 빠지면 보호자께 문자로 안내드리고 있어요.',
    alternative: '빠져야 하는 날은 전날까지 담임선생님께 채팅으로 알려 주시면 돼요.',
    situation: [{ when: '야간 자율학습 시간', where: '학교', what: '학생의 불참이 확인됨' }],
    concerns: ['불참 사유가 제대로 전달되지 않음'],
    requests: ['불참 연락 방법 안내'],
  },
  {
    match: /결석|지각|조퇴|출결|병원/,
    tag: 'LIFE_GUIDANCE',
    regulations: ['reg_9'],
    recommendation: 'EXPLAINED',
    empathy: '아이 건강 때문에 신경 쓰실 일이 많으셨겠어요.',
    reason: '학업성적관리규정에 따라 결석 후 5일 안에 진료확인서 같은 증빙 서류를 담임선생님께 내면 처리돼요.',
    alternative: '서류는 이 채팅에 사진으로 올려 주셔도 돼요.',
    situation: [{ when: '최근', where: '—', what: '학생이 결석함' }],
    concerns: ['결석 처리 방법을 알지 못함'],
    requests: ['출결 처리 절차 안내'],
  },
  {
    match: /교복|복장|체육복/,
    tag: 'LIFE_GUIDANCE',
    regulations: ['reg_8'],
    recommendation: 'EXPLAINED',
    empathy: '아이가 지적을 받아 속상해했을 것 같아요.',
    reason: '학생생활규정에 따라 교복을 단정히 입고, 체육 시간에는 체육복을 입도록 하고 있어요.',
    alternative: '계절별 착용 기준은 가정통신문으로 다시 안내드릴 수 있어요.',
    situation: [{ when: '등교 시간', where: '교문', what: '복장 지도를 받음' }],
    concerns: ['지도 기준이 명확하지 않다고 느낌'],
    requests: ['복장 기준 안내'],
  },
  {
    match: /다쳤|다치|부상|사고|안전|넘어/,
    tag: 'SAFETY',
    regulations: ['reg_law_safety'],
    recommendation: 'TALK_TO_TEACHER',
    empathy: '아이가 다쳤다는 소식에 많이 놀라셨겠어요.',
    reason: '교육활동 중 일어난 사고는 학교안전공제회에 보상을 청구할 수 있어요. 어떻게 다쳤는지는 선생님이 직접 확인해야 해요.',
    alternative: '병원 진료 기록은 꼭 보관해 주세요.',
    situation: [{ when: '교육활동 중', where: '—', what: '학생이 다침' }],
    concerns: ['학생의 부상 정도와 이후 회복'],
    requests: ['사고 경위 확인', '보상 청구 절차 안내'],
  },
  {
    match: /친구|따돌|괴롭|싸움|왕따|놀림|놀리/,
    tag: 'PEER_RELATION',
    regulations: [],
    recommendation: 'TALK_TO_TEACHER',
    empathy: '아이가 힘들어하는 모습을 보며 마음이 많이 아프셨겠어요.',
    reason: '친구 관계 문제는 학교 규정만으로 설명드리기 어려워요. 담임선생님이 상황을 직접 확인하시는 게 필요해요.',
    alternative: '아이가 말한 내용을 날짜별로 적어 두시면 선생님이 확인하는 데 도움이 돼요.',
    situation: [{ when: '최근', where: '학교', what: '친구 관계에서 어려움을 겪음' }],
    concerns: ['학생이 학교생활을 힘들어함'],
    requests: ['상황 확인과 지도'],
  },
];

const FALLBACK: Rule = {
  match: /.^/,
  tag: 'ETC',
  regulations: [],
  recommendation: 'TALK_TO_TEACHER',
  empathy: '말씀해 주셔서 고맙습니다. 많이 신경 쓰이셨겠어요.',
  reason: '말씀하신 내용과 바로 맞는 학교 규정을 찾지 못했어요. 선생님이 직접 확인하고 답해 주시는 게 정확해요.',
  alternative: '요약 카드를 만들어 선생님께 정리해서 전달해 드릴 수 있어요.',
  situation: [{ when: '최근', where: '—', what: '학부모님이 확인을 요청하신 일이 있음' }],
  concerns: [],
  requests: ['상황 확인'],
};

const ruleOf = (text: string) => RULES.find((r) => r.match.test(text)) ?? FALLBACK;

// ---------- 감정 표현 순화 ----------

const EMOTION =
  /(이게 )?말이 (됩니까|되나요|돼요|되냐)|이해가 안 ?(됩니다|돼요|가요|갑니다)|어이(가)? ?없|황당|화가 나|기가 막|도대체|어떻게 이럴|너무하|최악|무책임|제정신|가만 안|[!?]{2,}|ㅡㅡ|;;|ㅠ+|ㅜ+/g;

export const hasEmotion = (s: string) => new RegExp(EMOTION.source).test(s);

export function soften(s: string) {
  return s
    .replace(EMOTION, '')
    .replace(/[!?]+/g, '')
    .replace(/\s{2,}/g, ' ')
    .replace(/\s+([.,])/g, '$1')
    .trim();
}

// ---------- STEP 1: 규정 설명 ----------

export function explain(text: string): AiExplanation {
  const rule = ruleOf(text);
  return {
    empathy: rule.empathy,
    reason: rule.reason,
    alternative: rule.alternative,
    citations: rule.regulations.map(reg),
    recommendation: rule.recommendation,
  };
}

// ---------- STEP 2: 요약 카드 ----------

const WHEN = /(오늘|어제|그저께|그제|지난주|이번 주|\d{1,2}월 ?\d{1,2}일|\d교시|수업 ?중|점심시간|쉬는 ?시간|아침|종례|조회|방과 ?후|하교|등교)/;
const WHERE = /(교실|운동장|급식실|체육관|복도|화장실|도서관|과학실|음악실|교무실|교문|학원)/;
const REQUEST = /(주세요|주시면|주셨으면|바랍니다|원합니다|부탁|요청)/;
/** 연락 방법·시간 문장은 '희망 연락'으로 옮기고 상황·요청에서는 뺀다 */
const CONTACT_PREF = /(연락은|연락 ?주|전화로|통화로|문자로|방문해서|면담으로)/;
const CONCERN = /(걱정|불안|힘들어|속상|무서워|울었|잠을 못)/;

function sentencesOf(text: string) {
  return text
    .split(/(?<=[.!?…])\s+|\n+/)
    .map((s) => s.trim())
    .filter((s) => s.length > 1);
}

function parseTimeWindow(text: string): TimeRange | null {
  const m = text.match(/(오전|오후)?\s*(\d{1,2})(?::(\d{2}))?\s*시?\s*(?:~|-|부터|에서)\s*(오전|오후)?\s*(\d{1,2})(?::(\d{2}))?\s*시?/);
  if (!m) return null;
  const to24 = (ampm: string | undefined, h: string, fallbackPm: boolean) => {
    let hh = Number(h);
    if ((ampm === '오후' || (!ampm && fallbackPm)) && hh < 12) hh += 12;
    return hh;
  };
  const pm = m[1] === '오후';
  const fromH = to24(m[1], m[2], false);
  const toH = to24(m[4] ?? (pm ? '오후' : undefined), m[5], pm);
  if (fromH >= 24 || toH > 24 || fromH >= toH) return null;
  const pad = (n: number) => String(n).padStart(2, '0');
  return { from: `${pad(fromH)}:${m[3] ?? '00'}`, to: `${pad(toH)}:${m[6] ?? '00'}` };
}

function parseMethods(text: string): ContactMethod[] | null {
  const out: ContactMethod[] = [];
  if (/전화|통화/.test(text)) out.push('CALL');
  if (/방문|찾아뵙|면담|직접 만나/.test(text)) out.push('VISIT');
  if (/문자|메시지|채팅|글로/.test(text)) out.push('MESSAGE');
  return out.length ? out : null;
}

export function buildCard(text: string, student: UserSummary, receivedAt: string): Omit<SummaryCard, 'version'> {
  const rule = ruleOf(text);
  const sentences = sentencesOf(text);
  const n = student.studentNumber ?? '00000';

  const situation: SituationItem[] = [];
  const concerns: string[] = [];
  const requests: string[] = [];

  for (const s of sentences) {
    const clean = soften(s);
    if (!clean || clean.length < 4) continue; // 감정 표현만 있던 문장은 버린다
    if (CONTACT_PREF.test(s) && (parseMethods(s) || parseTimeWindow(s))) continue;
    if (REQUEST.test(s)) requests.push(clean);
    else if (CONCERN.test(s)) concerns.push(clean);
    else
      situation.push({
        order: situation.length + 1,
        when: s.match(WHEN)?.[1] ?? '—',
        where: s.match(WHERE)?.[1] ?? '—',
        what: clean,
      });
  }

  if (situation.length === 0) rule.situation.forEach((x, i) => situation.push({ order: i + 1, ...x }));
  for (const c of rule.concerns) if (!concerns.includes(c)) concerns.push(c);
  for (const r of rule.requests) if (!requests.includes(r)) requests.push(r);

  return {
    basicInfo: { grade: Number(n.slice(0, 1)), classNo: Number(n.slice(1, 3)), receivedAt, tags: [rule.tag] },
    attribution: '학부모님 설명에 따르면',
    situation: situation.slice(0, 5),
    concerns: concerns.slice(0, 4),
    requests: requests.slice(0, 5),
    preferredContact: { methods: parseMethods(text) ?? ['MESSAGE'], timeWindow: parseTimeWindow(text) },
    regulations: rule.regulations.map(reg),
    confirmedByParentAt: null,
  };
}

// ---------- 카드 수정 ----------

/** 카드 항목에 감정 표현이 있으면 순화 제안을 만든다 */
export function suggestionsFor(card: Pick<SummaryCard, 'situation' | 'concerns' | 'requests'>): CardSuggestion[] {
  const out: CardSuggestion[] = [];
  card.situation.forEach((s, i) => hasEmotion(s.what) && out.push({ field: 'situation', index: i, original: s.what, suggested: soften(s.what) }));
  card.concerns.forEach((s, i) => hasEmotion(s) && out.push({ field: 'concerns', index: i, original: s, suggested: soften(s) }));
  card.requests.forEach((s, i) => hasEmotion(s) && out.push({ field: 'requests', index: i, original: s, suggested: soften(s) }));
  return out.filter((x) => x.suggested && x.suggested !== x.original);
}

/** '수정사항 요청'(자연어)을 카드에 반영한다. 반영할 게 없으면 null. */
export function revise(card: SummaryCard, instruction: string): Omit<SummaryCard, 'version'> | null {
  const next = structuredClone(card);
  let changed = false;
  const quoted = [...instruction.matchAll(/['"‘’“”「」](.+?)['"‘’“”「」]/g)].map((m) => m[1].trim()).filter(Boolean);
  const removing = /빼|삭제|지워|없애/.test(instruction);

  if (quoted.length) {
    for (const q of quoted) {
      if (removing) {
        const before = next.requests.length + next.concerns.length + next.situation.length;
        next.requests = next.requests.filter((x) => !x.includes(q));
        next.concerns = next.concerns.filter((x) => !x.includes(q));
        next.situation = next.situation.filter((x) => !x.what.includes(q)).map((x, i) => ({ ...x, order: i + 1 }));
        changed ||= before !== next.requests.length + next.concerns.length + next.situation.length;
      } else if (/걱정|영향/.test(instruction)) {
        next.concerns.push(q);
        changed = true;
      } else if (/상황|있었던|경위/.test(instruction)) {
        next.situation.push({ order: next.situation.length + 1, when: q.match(WHEN)?.[1] ?? '—', where: q.match(WHERE)?.[1] ?? '—', what: q });
        changed = true;
      } else {
        next.requests.push(q);
        changed = true;
      }
    }
  }

  if (/연락|전화|통화|문자|메시지|방문|면담/.test(instruction)) {
    const methods = parseMethods(instruction);
    if (methods) {
      next.preferredContact.methods = methods;
      changed = true;
    }
  }
  const window = parseTimeWindow(instruction);
  if (window) {
    next.preferredContact.timeWindow = window;
    changed = true;
  }

  if (/유형|분류|태그/.test(instruction)) {
    const tag: ComplaintTag | null = /생활지도/.test(instruction)
      ? 'LIFE_GUIDANCE'
      : /학습|평가/.test(instruction)
        ? 'STUDY_EVALUATION'
        : /교우|친구/.test(instruction)
          ? 'PEER_RELATION'
          : /안전/.test(instruction)
            ? 'SAFETY'
            : null;
    if (tag) {
      next.basicInfo.tags = [tag];
      changed = true;
    }
  }

  if (!changed) {
    // 따옴표 없이 요청만 적은 경우: 요청 문장 자체를 요청 사항으로 정리
    const clean = soften(instruction.replace(/(요청 ?사항에|도|넣어 ?주세요|추가해 ?주세요|해 ?주세요)/g, ' ')).trim();
    if (clean.length < 4) return null;
    next.requests.push(clean);
  }

  const { version: _v, ...rest } = next;
  void _v;
  return { ...rest, confirmedByParentAt: null };
}

// ---------- STEP 3: 교사 입장 정리 ----------

const hasBatchim = (ch: string) => {
  const code = ch.charCodeAt(0) - 0xac00;
  return code >= 0 && code <= 11171 && code % 28 !== 0;
};
const endSentence = (s: string) => (/[.!?]$/.test(s.trim()) ? s.trim() : `${s.trim()}.`);

export function draftReply(body: ReplyDraftBody, student: UserSummary, teacher: UserSummary): ReplyDraft {
  const given = student.name.slice(1) || student.name;
  const nick = hasBatchim(given[given.length - 1]) ? `${given}이` : given;
  const situation = body.situation.trim() ? `당시 상황을 말씀드리면, ${endSentence(body.situation)}` : '';
  const reason = body.reason.trim() ? `그렇게 한 것은 ${body.reason.trim().replace(/[.]$/, '')}에 따른 것이었습니다.` : '';
  const nextActions = body.nextActions.trim() ? `앞으로는 ${endSentence(body.nextActions)}` : '';

  const draft = [
    `안녕하세요, ${nick} 담임 ${teacher.name}입니다.`,
    '보내 주신 내용 잘 읽었습니다. 걱정을 끼쳐 드려 마음이 쓰입니다.',
    situation,
    reason,
    nextActions,
    '더 궁금하신 점이 있으시면 편하게 말씀해 주세요. 감사합니다.',
  ]
    .filter(Boolean)
    .join('\n');

  return { draft, sections: { situation, reason, nextActions } };
}
