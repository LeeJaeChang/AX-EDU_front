import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  ApiError,
  complaintApi,
  type Attachment,
  type CardSuggestion,
  type ComplaintRecipient,
  type ConsultMessage,
  type ConsultStage,
  type QuickReply,
  type SummaryCardResult,
} from '../api';
import { newClientId } from '../api/client';
import CardEditorModal from '../components/complaint/CardEditorModal';
import ExplanationView from '../components/complaint/ExplanationView';
import StatusPill from '../components/complaint/StatusPill';
import SummaryCardView from '../components/complaint/SummaryCardView';
import Composer from '../components/Composer';
import { ChevronLeftIcon } from '../components/Icons';
import { useComplaint } from '../hooks/useComplaint';
import { useMe } from '../hooks/useMe';

type Busy = 'explaining' | 'retrying' | 'consulting' | 'card' | 'revising' | 'editing' | 'submitting' | 'closing' | null;

interface LogEntry {
  key: string;
  kind: 'instruction' | 'revised' | 'error';
  text: string;
}

const STEPS = ['공감', '사실·규정 확인', '선생님 정하기', '요약 카드', '전달'] as const;

const PLACEHOLDER: Record<ConsultStage, string> = {
  FACTS: '편하게 답해 주세요. 위의 버튼을 눌러도 괜찮아요.',
  EXPLAIN: '더 하실 말씀이나 고칠 내용이 있으면 적어 주세요.',
  RECIPIENT: '전하고 싶은 선생님을 적거나 위에서 골라 주세요.',
  CONTACT: '편한 연락 방법이나 시간을 적어 주세요.',
  READY: '더 하실 말씀이 있으면 적어 주세요.',
};

/**
 * 학부모 전용 AI 민원 상담 (기획안 STEP 1·2)
 * 채팅 목록 '민원하기' 탭 → 상담사가 공감하며 이야기를 듣고 → 한 가지씩 물어 사실관계를 확인
 * → 관련 규정·법령 설명 → 누구에게 전할지 함께 추론 → 연락 방법·시간 → 요약 카드 → 확인하고 보내기
 * 이 화면의 대화는 선생님께 보이지 않는다. 보낸 카드만 그 선생님과의 채팅방에 올라간다.
 */
export default function ComplaintChatbotPage() {
  const { complaintId: routeId } = useParams();
  const navigate = useNavigate();
  const me = useMe();

  const [complaintId, setComplaintId] = useState<string | null>(routeId ?? null);
  const { complaint, setComplaint, error: loadError } = useComplaint(complaintId);
  const [pendingText, setPendingText] = useState<string | null>(null);
  const [pendingReply, setPendingReply] = useState<string | null>(null);
  const [busy, setBusy] = useState<Busy>(null);
  const [error, setError] = useState<string | null>(null);
  const [log, setLog] = useState<LogEntry[]>([]);
  const [suggestions, setSuggestions] = useState<CardSuggestion[]>([]);
  const [editing, setEditing] = useState(false);
  const [studentId, setStudentId] = useState(me.childStudentIds?.[0] ?? '');
  const [recipients, setRecipients] = useState<ComplaintRecipient[]>([]);
  const [teacherId, setTeacherId] = useState<string | null>(null);
  const createKey = useRef(newClientId('cmp_'));
  const submitKey = useRef(newClientId('sub_'));

  useEffect(() => setComplaintId(routeId ?? null), [routeId]);

  const card = complaint?.summaryCard ?? null;
  const status = complaint?.status;
  const stage = complaint?.consultStage ?? null;
  const transcript: ConsultMessage[] = complaint?.consultation ?? [];

  // 요약 카드 단계: 받을 선생님 후보 (상담에서 정한 분이 기본)
  useEffect(() => {
    if (status !== 'CARD_DRAFTING' || !complaint) return;
    let alive = true;
    complaintApi
      .listRecipients(complaint.id)
      .then((list) => {
        if (!alive) return;
        setRecipients(list);
        setTeacherId((cur) => cur ?? list.find((r) => r.recommended)?.teacher.id ?? list[0]?.teacher.id ?? null);
      })
      .catch((e: Error) => alive && setError(e.message));
    return () => {
      alive = false;
    };
  }, [status, complaint?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  const chosen = recipients.find((r) => r.teacher.id === teacherId) ?? null;
  const sentTo = complaint?.teacher?.name;

  // ---- 행동 ----

  async function create(content: string, attachments: Attachment[]) {
    if (!studentId) {
      setError('자녀 정보가 없어 상담을 시작할 수 없어요.');
      throw new Error('no child');
    }
    setPendingText(content);
    setBusy('explaining');
    setError(null);
    try {
      const c = await complaintApi.create({ studentId, content, attachmentIds: attachments.map((a) => a.id) }, createKey.current);
      setComplaint(c);
      setComplaintId(c.id);
      navigate(`/complaint/${c.id}`, { replace: true });
    } catch (e) {
      // 422: 원문은 저장됨 → 그 상담으로 이어서 재시도
      if (e instanceof ApiError && e.code === 'AI_PROCESSING_FAILED' && e.details.complaintId) {
        const id = String(e.details.complaintId);
        setComplaintId(id);
        navigate(`/complaint/${id}`, { replace: true });
      } else {
        setPendingText(null);
        setError((e as Error).message);
        throw e;
      }
    } finally {
      setBusy(null);
    }
  }

  async function run<T>(kind: Busy, fn: () => Promise<T>) {
    setBusy(kind);
    setError(null);
    try {
      return await fn();
    } catch (e) {
      setError((e as Error).message);
      throw e;
    } finally {
      setBusy(null);
    }
  }

  async function consult(content: string, value?: string) {
    if (!complaint) return;
    setPendingReply(content);
    try {
      await run('consulting', async () => setComplaint(await complaintApi.consult(complaint.id, content, value)));
    } finally {
      setPendingReply(null);
    }
  }

  const applyCard = (next: SummaryCardResult) => {
    setSuggestions(next.suggestions ?? []);
    setComplaint((c) => (c ? { ...c, summaryCard: next, status: 'CARD_DRAFTING', consultStage: null } : c));
  };

  const retry = () => run('retrying', async () => setComplaint(await complaintApi.retryExplanation(complaint!.id))).catch(() => {});
  const close = () => run('closing', async () => setComplaint(await complaintApi.close(complaint!.id))).catch(() => {});
  const makeCard = () => run('card', async () => applyCard(await complaintApi.createSummaryCard(complaint!.id))).catch(() => {});

  const onQuick = (q: QuickReply) => {
    if (q.value === 'CLOSE') return close();
    if (q.value === 'MAKE_CARD') return makeCard();
    consult(q.label, q.value).catch(() => {});
  };

  async function revise(instruction: string) {
    if (!complaint || !card) return;
    const key = newClientId('rev_');
    setLog((l) => [...l, { key, kind: 'instruction', text: instruction }]);
    try {
      const next = await run('revising', () => complaintApi.requestRevision(complaint.id, card.version, instruction));
      applyCard(next);
      setLog((l) => [...l, { key: `${key}_ok`, kind: 'revised', text: `말씀하신 대로 고쳤어요. (v${next.version}) 다시 한번 확인해 주시겠어요?` }]);
    } catch (e) {
      setLog((l) => [...l, { key: `${key}_err`, kind: 'error', text: (e as Error).message }]);
      setError(null);
    }
  }

  async function applySuggestion(s: CardSuggestion) {
    if (!complaint || !card) return;
    const fields =
      s.field === 'situation'
        ? { situation: card.situation.map((x, i) => (i === s.index ? { ...x, what: s.suggested } : x)) }
        : { [s.field]: (card[s.field] as string[]).map((x, i) => (i === s.index ? s.suggested : x)) };
    await run('editing', async () => applyCard(await complaintApi.editSummaryCard(complaint.id, card.version, fields))).catch(() => {});
  }

  const submit = () => {
    if (!teacherId) {
      setError('받을 선생님을 골라 주세요.');
      return;
    }
    run('submitting', async () => setComplaint(await complaintApi.submit(complaint!.id, card!.version, teacherId, submitKey.current))).catch(() => {});
  };

  // ---- 입력창 모드 ----
  type Out = { content: string; attachments: Attachment[] };
  const locked = (placeholder: string) => ({ placeholder, disabled: true, attach: false, onSend: async () => {} });
  const composer: { placeholder: string; disabled: boolean; attach: boolean; onSend: (o: Out) => Promise<unknown> } =
    !complaint && !pendingText
      ? { placeholder: '어떤 일이 있었는지 편하게 적어 주세요. 선생님께 바로 가지 않아요.', disabled: busy !== null, attach: true, onSend: (o) => create(o.content, o.attachments) }
      : status === 'EXPLAINED' && stage
        ? { placeholder: PLACEHOLDER[stage], disabled: busy !== null, attach: false, onSend: (o) => consult(o.content) }
        : status === 'CARD_DRAFTING'
          ? { placeholder: "고칠 점을 말로 적어 주세요. 예: 요청 사항에 '보관 시간 단축 검토'도 넣어 주세요", disabled: busy !== null, attach: false, onSend: (o) => revise(o.content) }
          : status === 'EXPLAINED'
            ? locked("위의 '다시 시도'를 눌러 주세요.")
            : status === 'CLOSED_BY_EXPLANATION'
              ? locked('설명으로 마친 상담이에요.')
              : status
                ? locked('요약 카드를 보냈어요. 이후 대화는 선생님과의 채팅방에서 이어 주세요.')
                : locked('이야기를 듣고 있어요…');

  // ---- 스크롤 ----
  const scroller = useRef<HTMLDivElement>(null);
  useLayoutEffect(() => {
    const el = scroller.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [complaint, busy, log.length, pendingText, pendingReply, suggestions.length, error, recipients.length]);

  const reached = (s: typeof status) => !!status && order(status) >= order(s!);
  const canAct = busy === null;
  const last = transcript[transcript.length - 1];
  const showQuick = status === 'EXPLAINED' && canAct && !pendingReply && last?.from === 'AI';
  const aiFailed = status === 'EXPLAINED' && !stage && canAct && last?.from === 'PARENT';

  // 0 공감 → 1 사실·규정 확인 → 2 선생님 정하기 → 3 요약 카드 → 전달 완료면 모두 끝남
  const step = !status
    ? 0
    : status === 'EXPLAINED'
      ? !stage || (stage === 'FACTS' && transcript.length <= 3)
        ? 0
        : stage === 'FACTS' || stage === 'EXPLAIN'
          ? 1
          : stage === 'READY'
            ? 3
            : 2
      : status === 'CARD_DRAFTING'
        ? 3
        : STEPS.length;

  return (
    <div className="room-page">
      <nav className="topbar">
        <Link to="/chat" className="back-link">
          <ChevronLeftIcon width={18} height={18} />
          민원하기로
        </Link>
      </nav>

      <div className="room-body">
        <header className="room-head room-head--bot">
          <span className="avatar avatar--ai" style={{ width: 40, height: 40 }} aria-hidden="true">AI</span>
          <div className="room-head__text">
            <h1 className="room-head__name">AI 민원 상담</h1>
            <p className="room-head__sub">상담 내용은 선생님께 보이지 않아요. 마지막에 확인하고 보낸 요약 카드만 전달돼요.</p>
          </div>
          {status && <StatusPill status={status} viewerRole="PARENT" />}
        </header>

        {status !== 'CLOSED_BY_EXPLANATION' && (
          <ol className="flow-steps" aria-label="상담 진행 단계">
            {STEPS.map((label, i) => (
              <li key={label} className={`flow-steps__item ${i < step ? 'is-done' : ''} ${i === step ? 'is-current' : ''}`} aria-current={i === step ? 'step' : undefined}>
                <span className="flow-steps__num tnum">{i + 1}</span>
                {label}
              </li>
            ))}
          </ol>
        )}

        <div className="messages-scroll" ref={scroller}>
          <ol className="messages">
            <Bot>
              <p>안녕하세요, AI 상담사예요. 학교 일로 마음 쓰이는 일이 있으셨군요.</p>
              <p>어떤 일이 있었는지 편하게 들려주세요. 정리되지 않은 말이어도 괜찮아요.</p>
              <p className="bot-sub">
                먼저 이야기를 충분히 듣고, 있었던 일을 하나씩 여쭤보며 관련 규정과 법령을 찾아 드릴게요. 누구에게 전하면 좋을지도 함께 정한 뒤, 선생님이 보기 쉬운 요약 카드로 정리해 드려요. 이 상담은 선생님께 보이지 않으니 안심하고 말씀하셔도 돼요.
              </p>
              {!complaint && (me.childStudentIds?.length ?? 0) > 1 && (
                <div className="chips" role="group" aria-label="자녀 선택">
                  {me.childStudentIds!.map((id, i) => (
                    <button key={id} type="button" className="chip-btn" aria-pressed={studentId === id} onClick={() => setStudentId(id)}>
                      자녀 {i + 1}
                    </button>
                  ))}
                </div>
              )}
            </Bot>

            {/* 상담 기록이 없는 예전 민원은 원문만 보여 준다 */}
            {!complaint && pendingText && <Mine>{pendingText}</Mine>}
            {complaint && transcript.length === 0 && complaint.originalText && <Mine>{complaint.originalText}</Mine>}

            {transcript.map((m, i) =>
              m.from === 'PARENT' ? (
                <Mine key={m.id}>{m.content}</Mine>
              ) : (
                <Bot key={m.id} continued={transcript[i - 1]?.from === 'AI'}>
                  <AiMessage m={m} quick={showQuick && m === last ? onQuick : undefined} />
                </Bot>
              ),
            )}

            {(busy === 'explaining' || busy === 'retrying') && <Typing text="이야기를 듣고 있어요" />}

            {aiFailed && (
              <Bot tone="warn">
                <p>죄송해요, 답을 드리는 데 시간이 걸리고 있어요. 적어 주신 이야기는 잘 저장되어 있어요.</p>
                <div className="bot-actions">
                  <button type="button" className="btn btn--sm" onClick={retry}>다시 시도</button>
                </div>
              </Bot>
            )}

            {pendingReply && (
              <>
                <Mine>{pendingReply}</Mine>
                <Typing text="상담사가 답을 적고 있어요" />
              </>
            )}

            {(status === 'CLOSED_BY_EXPLANATION' || busy === 'closing') && (
              <>
                <Mine>설명으로 충분해요</Mine>
                {status === 'CLOSED_BY_EXPLANATION' && (
                  <Bot>
                    <p>오늘 이야기 나눠 주셔서 고마워요. 궁금하셨던 점이 조금이나마 풀리셨다니 다행이에요.</p>
                    <p className="bot-sub">이 상담은 선생님께 전달되지 않았어요. 다시 마음 쓰이는 일이 생기면 언제든 찾아 주세요.</p>
                    <div className="bot-actions">
                      <Link className="btn btn--soft btn--sm" to="/chat">민원하기로</Link>
                    </div>
                  </Bot>
                )}
              </>
            )}

            {(reached('CARD_DRAFTING') || busy === 'card') && <Mine>요약 카드 만들기</Mine>}
            {busy === 'card' && <Typing text="상담 내용을 요약 카드로 정리하고 있어요" />}

            {card && reached('CARD_DRAFTING') && (
              <Bot wide>
                <p>
                  함께 정리한 내용을 {complaint?.proposedTeacher?.name ? `${complaint.proposedTeacher.name} 선생님이` : '선생님이'} 보기 쉽게 한 장으로 담았어요. 감정 표현은 덜고, 있었던 일과 바라시는 점 위주로 적었어요.
                </p>
                {log.length === 0 && status === 'CARD_DRAFTING' && (
                  <p className="bot-sub">천천히 읽어 보시고, 고칠 부분이 있으면 아래 입력창에 말로 적거나 ‘직접 고치기’를 눌러 주세요.</p>
                )}
              </Bot>
            )}

            {log.map((l) =>
              l.kind === 'instruction' ? (
                <Mine key={l.key}>{l.text}</Mine>
              ) : (
                <Bot key={l.key} tone={l.kind === 'error' ? 'warn' : undefined}>
                  <p>{l.text}</p>
                </Bot>
              ),
            )}
            {busy === 'revising' && <Typing text="카드를 고치고 있어요" />}

            {card && reached('CARD_DRAFTING') && (
              <li className="msg msg--theirs msg--card">
                <BotAvatar />
                <div className="msg__col msg__col--wide">
                  <SummaryCardView
                    card={card}
                    suggestions={status === 'CARD_DRAFTING' ? suggestions : undefined}
                    onApplySuggestion={applySuggestion}
                    busy={!canAct}
                  />
                  {status === 'CARD_DRAFTING' && (
                    <div className="send-panel">
                      <fieldset className="recipients">
                        <legend className="recipients__legend">받는 선생님</legend>
                        {recipients.length === 0 && <p className="bot-sub">선생님 목록을 불러오는 중…</p>}
                        {recipients.map((r) => (
                          <label key={r.teacher.id} className={`recipient ${teacherId === r.teacher.id ? 'is-selected' : ''}`}>
                            <input
                              type="radio"
                              name="recipient"
                              value={r.teacher.id}
                              checked={teacherId === r.teacher.id}
                              onChange={() => setTeacherId(r.teacher.id)}
                              disabled={!canAct}
                            />
                            <span className="recipient__body">
                              <span className="recipient__name">
                                {r.teacher.name} 선생님
                                {r.recommended && <span className="pill pill--accent">상담에서 정함</span>}
                              </span>
                              <span className="recipient__label">{r.label}</span>
                            </span>
                          </label>
                        ))}
                      </fieldset>
                      <div className="bot-actions bot-actions--card">
                        <button type="button" className="btn btn--ghost btn--sm" onClick={() => setEditing(true)} disabled={!canAct}>직접 고치기</button>
                        <button type="button" className="btn btn--sm" onClick={submit} disabled={!canAct || !teacherId}>
                          {busy === 'submitting' ? '보내는 중…' : chosen ? `${chosen.teacher.name} 선생님께 보내기` : '확인하고 보내기'}
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              </li>
            )}

            {reached('SUBMITTED') && (
              <>
                <Mine>{sentTo ? `${sentTo} 선생님께 보내기` : '확인하고 보내기'}</Mine>
                <Bot>
                  <p>{sentTo ?? ''} 선생님께 요약 카드를 전해 드렸어요.</p>
                  <p className="bot-sub">선생님이 대화 가능 시간 안에 카드를 확인하고 답을 드릴 거예요. 답이 오면 그 선생님과의 채팅방에서 알려 드릴게요.</p>
                  <p>오늘 마음을 터놓고 이야기해 주셔서 고마워요.</p>
                  <div className="bot-actions">
                    {complaint?.roomId && <Link className="btn btn--sm" to={`/chat/${complaint.roomId}`}>채팅방에서 보기</Link>}
                    <Link className="btn btn--ghost btn--sm" to="/chat">민원하기로</Link>
                  </div>
                </Bot>
              </>
            )}
          </ol>
          {(error || loadError) && <p className="inline-error" role="alert">{error ?? loadError}</p>}
        </div>

        <Composer
          key={status ?? 'new'}
          placeholder={composer.placeholder}
          disabled={composer.disabled}
          allowAttach={composer.attach}
          restoreOnError
          onSend={composer.onSend}
        />
      </div>

      {editing && complaint && card && (
        <CardEditorModal complaintId={complaint.id} card={card} onClose={() => setEditing(false)} onSaved={applyCard} />
      )}
    </div>
  );
}

const ORDER = ['EXPLAINED', 'CLOSED_BY_EXPLANATION', 'CARD_DRAFTING', 'SUBMITTED', 'TEACHER_REVIEWING', 'IN_CONVERSATION', 'ESCALATED', 'RESOLVED'];
function order(s: string) {
  // CLOSED_BY_EXPLANATION은 카드 단계로 가지 않는다
  if (s === 'CLOSED_BY_EXPLANATION') return 1;
  return ORDER.indexOf(s);
}

/** 상담사 메시지 한 개: 본문 → 보조 설명 → 사실관계 표 / 규정·법령 / 추론 근거 → 빠른 답 */
function AiMessage({ m, quick }: { m: ConsultMessage; quick?: (q: QuickReply) => void }) {
  return (
    <>
      {m.content.split('\n').map((line, i) => (
        <p key={i}>{line}</p>
      ))}
      {m.facts && (
        <dl className="facts">
          {m.facts.map((f) => (
            <div key={f.label} className="facts__row">
              <dt>{f.label}</dt>
              <dd className={f.value === '확인 필요' || f.value === '—' ? 'is-empty' : undefined}>{f.value}</dd>
            </div>
          ))}
        </dl>
      )}
      {m.explanation && <ExplanationView explanation={m.explanation} />}
      {m.reasoning && (
        <ul className="reasoning" aria-label="이렇게 생각한 이유">
          {m.reasoning.map((r) => (
            <li key={r}>{r}</li>
          ))}
        </ul>
      )}
      {m.note && <p className="bot-sub">{m.note}</p>}
      {quick && m.quickReplies && m.quickReplies.length > 0 && (
        <div className="quick-replies" role="group" aria-label="빠른 답">
          {m.quickReplies.map((q) => (
            <button
              key={q.label}
              type="button"
              className={`quick-reply ${q.value === 'MAKE_CARD' || q.value === 'TO_TEACHER' || q.label.includes('(추천)') ? 'quick-reply--primary' : ''}`}
              onClick={() => quick(q)}
            >
              {q.label}
            </button>
          ))}
        </div>
      )}
    </>
  );
}

function BotAvatar({ hidden }: { hidden?: boolean }) {
  return (
    <span className={`avatar avatar--ai ${hidden ? 'avatar--ghost' : ''}`} style={{ width: 36, height: 36 }} aria-hidden="true">
      {hidden ? '' : 'AI'}
    </span>
  );
}

function Bot({ children, tone, wide, continued }: { children: ReactNode; tone?: 'warn'; wide?: boolean; continued?: boolean }) {
  return (
    <li className={`msg msg--theirs ${continued ? 'msg--cont' : ''}`}>
      <BotAvatar hidden={continued} />
      <div className={`msg__col ${wide ? 'msg__col--wide' : ''}`}>
        {!continued && <span className="msg__sender">AI 상담사</span>}
        <div className={`bubble bubble--theirs bubble--bot ${tone === 'warn' ? 'bubble--warn' : ''}`}>{children}</div>
      </div>
    </li>
  );
}

function Mine({ children }: { children: ReactNode }) {
  return (
    <li className="msg msg--mine">
      <div className="bubble bubble--mine">
        <p className="bubble__text">{children}</p>
      </div>
    </li>
  );
}

function Typing({ text }: { text: string }) {
  return (
    <li className="msg msg--theirs" aria-live="polite">
      <BotAvatar />
      <div className="bubble bubble--theirs bubble--typing">
        <span>{text}</span>
        <span className="dots" aria-hidden="true">
          <i />
          <i />
          <i />
        </span>
      </div>
    </li>
  );
}
