import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  ApiError,
  chatApi,
  complaintApi,
  type Attachment,
  type CardSuggestion,
  type ChatRoomDetail,
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

type Busy = 'explaining' | 'retrying' | 'card' | 'revising' | 'editing' | 'submitting' | 'closing' | null;

interface LogEntry {
  key: string;
  kind: 'instruction' | 'revised' | 'error';
  text: string;
}

/**
 * 학부모 전용 AI 챗봇 영역 (명세서 7.3~7.6, 기획안 STEP 1·2)
 * 최초 민원 → AI 규정 설명 → (종료 | 요약 카드) → 수정사항 요청·직접 수정 → 확인하고 보내기
 * 이 화면의 대화는 교사에게 전달되지 않는다. '확인하고 보내기'를 누른 카드만 채팅방에 올라간다.
 */
export default function ComplaintChatbotPage() {
  const { roomId = '', complaintId: routeId } = useParams();
  const navigate = useNavigate();
  const me = useMe();

  const [room, setRoom] = useState<ChatRoomDetail | null>(null);
  const [complaintId, setComplaintId] = useState<string | null>(routeId ?? null);
  const { complaint, setComplaint, error: loadError } = useComplaint(complaintId);
  const [pendingText, setPendingText] = useState<string | null>(null);
  const [busy, setBusy] = useState<Busy>(null);
  const [error, setError] = useState<string | null>(null);
  const [log, setLog] = useState<LogEntry[]>([]);
  const [suggestions, setSuggestions] = useState<CardSuggestion[]>([]);
  const [editing, setEditing] = useState(false);
  const [studentId, setStudentId] = useState(me.childStudentIds?.[0] ?? '');
  const createKey = useRef(newClientId('cmp_'));
  const submitKey = useRef(newClientId('sub_'));

  useEffect(() => {
    chatApi.getRoom(roomId).then(setRoom).catch((e: Error) => setError(e.message));
  }, [roomId]);

  useEffect(() => setComplaintId(routeId ?? null), [routeId]);

  const card = complaint?.summaryCard ?? null;
  const status = complaint?.status;
  const teacherName = room?.counterpart?.name ?? '선생님';

  // ---- 행동 ----

  async function create(content: string, attachments: Attachment[]) {
    if (!studentId) {
      setError('자녀 정보가 없어 민원을 쓸 수 없어요.');
      throw new Error('no child');
    }
    setPendingText(content);
    setBusy('explaining');
    setError(null);
    try {
      const c = await complaintApi.create(
        { roomId, studentId, content, attachmentIds: attachments.map((a) => a.id) },
        createKey.current,
      );
      setComplaint(c);
      setComplaintId(c.id);
      navigate(`/chat/${roomId}/complaint/${c.id}`, { replace: true });
    } catch (e) {
      // 422: 원문은 저장됨 → 그 민원으로 이어서 재시도
      if (e instanceof ApiError && e.code === 'AI_PROCESSING_FAILED' && e.details.complaintId) {
        const id = String(e.details.complaintId);
        setComplaintId(id);
        navigate(`/chat/${roomId}/complaint/${id}`, { replace: true });
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

  const applyCard = (next: SummaryCardResult) => {
    setSuggestions(next.suggestions ?? []);
    setComplaint((c) => (c ? { ...c, summaryCard: next, status: 'CARD_DRAFTING' } : c));
  };

  const retry = () => run('retrying', async () => setComplaint(await complaintApi.retryExplanation(complaint!.id))).catch(() => {});
  const close = () => run('closing', async () => setComplaint(await complaintApi.close(complaint!.id))).catch(() => {});
  const makeCard = () => run('card', async () => applyCard(await complaintApi.createSummaryCard(complaint!.id))).catch(() => {});

  async function revise(instruction: string) {
    if (!complaint || !card) return;
    const key = newClientId('rev_');
    setLog((l) => [...l, { key, kind: 'instruction', text: instruction }]);
    try {
      const next = await run('revising', () => complaintApi.requestRevision(complaint.id, card.version, instruction));
      applyCard(next);
      setLog((l) => [...l, { key: `${key}_ok`, kind: 'revised', text: `말씀하신 대로 카드를 고쳤어요. (v${next.version})` }]);
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

  const submit = () =>
    run('submitting', async () => {
      const c = await complaintApi.submit(complaint!.id, card!.version, submitKey.current);
      setComplaint(c);
    }).catch(() => {});

  // ---- 입력창 모드 ----
  type Out = { content: string; attachments: Attachment[] };
  const composer: { placeholder: string; disabled: boolean; attach: boolean; onSend: (o: Out) => Promise<unknown> } =
    !complaint && !pendingText
      ? { placeholder: '어떤 일이 있었는지 편하게 적어 주세요. 선생님께 바로 가지 않아요.', disabled: busy !== null, attach: true, onSend: (o) => create(o.content, o.attachments) }
      : status === 'CARD_DRAFTING'
        ? { placeholder: "고칠 점을 말로 적어 주세요. 예: 요청 사항에 '보관 시간 단축 검토'도 넣어 주세요", disabled: busy !== null, attach: false, onSend: (o) => revise(o.content) }
        : { placeholder: status === 'EXPLAINED'
              ? '위 버튼으로 다음 단계를 골라 주세요.'
              : status === 'CLOSED_BY_EXPLANATION'
                ? '설명으로 마친 민원이에요.'
                : '요약 카드를 보냈어요. 이후 대화는 채팅방에서 이어 주세요.', disabled: true, attach: false, onSend: async () => {} };

  // ---- 스크롤 ----
  const scroller = useRef<HTMLDivElement>(null);
  useLayoutEffect(() => {
    const el = scroller.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [complaint, busy, log.length, pendingText, suggestions.length, error]);

  const original = complaint?.originalText ?? pendingText;
  const explanation = complaint?.aiExplanation ?? null;
  const reached = (s: typeof status) => !!status && order(status) >= order(s!);
  const canAct = busy === null;

  return (
    <div className="room-page">
      <nav className="topbar">
        <Link to={`/chat/${roomId}`} className="back-link">
          <ChevronLeftIcon width={18} height={18} />
          {teacherName} 선생님과의 채팅으로
        </Link>
      </nav>

      <div className="room-body">
        <header className="room-head room-head--bot">
          <span className="avatar avatar--ai" style={{ width: 64, height: 64 }} aria-hidden="true">AI</span>
          <div className="room-head__text">
            <h1 className="room-head__name">AI 민원 도우미</h1>
            <p className="room-head__sub">이 대화는 {teacherName} 선생님께 보이지 않아요. ‘확인하고 보내기’를 누른 요약 카드만 전달돼요.</p>
          </div>
          {status && <StatusPill status={status} viewerRole="PARENT" />}
        </header>

        <div className="messages-scroll" ref={scroller}>
          <ol className="messages">
            <Bot>
              <p>안녕하세요. {teacherName} 선생님께 전하고 싶은 일이 있으신가요?</p>
              <p className="bot-sub">먼저 학교 규정에서 관련 내용을 찾아 설명드릴게요. 규정으로 풀리지 않으면 선생님이 보기 쉽게 요약해서 전해 드려요.</p>
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

            {original && <Mine>{original}</Mine>}

            {(busy === 'explaining' || busy === 'retrying') && <Typing text="학교 규정을 찾고 있어요" />}

            {complaint && !explanation && busy === null && status === 'EXPLAINED' && (
              <Bot tone="warn">
                <p>AI 응답이 늦어지고 있어요. 적어 주신 내용은 저장되어 있어요.</p>
                <div className="bot-actions">
                  <button type="button" className="btn btn--sm" onClick={retry}>다시 시도</button>
                </div>
              </Bot>
            )}

            {explanation && (
              <Bot>
                <ExplanationView explanation={explanation} />
                {status === 'EXPLAINED' && (
                  <>
                    <p className="bot-sub bot-sub--q">
                      {explanation.recommendation === 'TALK_TO_TEACHER'
                        ? '규정만으로는 답드리기 어려운 내용이에요. 선생님께 정리해서 전해 드릴까요?'
                        : '설명으로 궁금한 점이 풀리셨나요? 그래도 선생님과 이야기하고 싶으시면 요약해서 전해 드릴게요.'}
                    </p>
                    <div className="bot-actions">
                      {explanation.recommendation === 'TALK_TO_TEACHER' ? (
                        <>
                          <button type="button" className="btn btn--sm" onClick={makeCard} disabled={!canAct}>선생님과 대화하기</button>
                          <button type="button" className="btn btn--ghost btn--sm" onClick={close} disabled={!canAct}>설명으로 충분해요</button>
                        </>
                      ) : (
                        <>
                          <button type="button" className="btn btn--sm" onClick={close} disabled={!canAct}>설명으로 충분해요</button>
                          <button type="button" className="btn btn--soft btn--sm" onClick={makeCard} disabled={!canAct}>그래도 선생님과 대화하기</button>
                        </>
                      )}
                    </div>
                  </>
                )}
              </Bot>
            )}

            {(status === 'CLOSED_BY_EXPLANATION' || busy === 'closing') && (
              <>
                <Mine>설명으로 충분해요</Mine>
                {status === 'CLOSED_BY_EXPLANATION' && (
                  <Bot>
                    <p>도움이 되었다니 다행이에요. 이 대화는 선생님께 전달되지 않았어요.</p>
                    <div className="bot-actions">
                      <Link className="btn btn--soft btn--sm" to={`/chat/${roomId}`}>채팅으로 돌아가기</Link>
                    </div>
                  </Bot>
                )}
              </>
            )}

            {(reached('CARD_DRAFTING') || busy === 'card') && <Mine>선생님과 대화하고 싶어요</Mine>}
            {busy === 'card' && <Typing text="선생님께 드릴 요약 카드를 만들고 있어요" />}

            {card && reached('CARD_DRAFTING') && (
              <Bot wide>
                <p>말씀하신 내용을 {teacherName} 선생님이 보기 쉽게 정리했어요. 감정 표현은 덜고 있었던 일과 요청 위주로 담았어요.</p>
                {log.length === 0 && status === 'CARD_DRAFTING' && <p className="bot-sub">고칠 점이 있으면 아래 입력창에 말로 적거나 ‘직접 고치기’를 눌러 주세요.</p>}
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
                    <div className="bot-actions bot-actions--card">
                      <button type="button" className="btn btn--ghost btn--sm" onClick={() => setEditing(true)} disabled={!canAct}>직접 고치기</button>
                      <button type="button" className="btn btn--sm" onClick={submit} disabled={!canAct}>
                        {busy === 'submitting' ? '보내는 중…' : '확인하고 보내기'}
                      </button>
                    </div>
                  )}
                </div>
              </li>
            )}

            {reached('SUBMITTED') && (
              <>
                <Mine>확인하고 보내기</Mine>
                <Bot>
                  <p>{teacherName} 선생님께 요약 카드를 보냈어요.</p>
                  <p className="bot-sub">선생님은 대화 가능 시간 안에 카드를 확인하고 답해 주세요. 답이 오면 채팅방에서 알려 드릴게요.</p>
                  <div className="bot-actions">
                    <Link className="btn btn--sm" to={`/chat/${roomId}`}>채팅방에서 보기</Link>
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

function BotAvatar() {
  return (
    <span className="avatar avatar--ai" style={{ width: 48, height: 48 }} aria-hidden="true">
      AI
    </span>
  );
}

function Bot({ children, tone, wide }: { children: ReactNode; tone?: 'warn'; wide?: boolean }) {
  return (
    <li className="msg msg--theirs">
      <BotAvatar />
      <div className={`msg__col ${wide ? 'msg__col--wide' : ''}`}>
        <span className="msg__sender">AI 민원 도우미</span>
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
