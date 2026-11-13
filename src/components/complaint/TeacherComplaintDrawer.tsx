import { useEffect, useRef, useState } from 'react';
import { ApiError, complaintApi, type ContactMethod } from '../../api';
import { useComplaint } from '../../hooks/useComplaint';
import { CONTACT_LABEL, ESC_STATUS_LABEL, REASON_LABEL, STAGE_LABEL } from '../../lib/complaint';
import { formatDateTime, localToKstIso, senderLabel } from '../../lib/format';
import { CloseIcon } from '../Icons';
import ConfirmModal from '../ConfirmModal';
import EscalationModal from './EscalationModal';
import StatusPill from './StatusPill';
import SummaryCardView from './SummaryCardView';

const METHODS: ContactMethod[] = ['MESSAGE', 'CALL', 'VISIT'];
const METHOD_HINT: Record<ContactMethod, string> = {
  MESSAGE: '이 채팅방에 답변 메시지만 보냅니다.',
  CALL: '메시지와 함께 슈퍼스쿨 안심전화를 예약합니다. 선생님 개인번호는 학부모님께 보이지 않아요.',
  VISIT: '메시지와 함께 슈퍼스쿨 면담 예약을 만듭니다. 방문 당일 슈퍼패스로 보호자를 확인해요.',
};

interface Props {
  complaintId: string;
  onClose: () => void;
}

/**
 * 교사 화면: 민원 요약 → AI 입장 정리 → 학부모에게 보낼 연락 → (필요 시) 민원 대응팀 도움 요청
 * 명세서 7.8~7.10, 기획안 STEP 3~5. 학부모 원문과 AI 설명은 교사에게 내려오지 않는다.
 */
export default function TeacherComplaintDrawer({ complaintId, onClose }: Props) {
  const ref = useRef<HTMLDialogElement>(null);
  const { complaint, setComplaint, error: loadError, reload } = useComplaint(complaintId);
  const acked = useRef(false);
  const methodInit = useRef(false);

  const [memo, setMemo] = useState({ situation: '', reason: '', nextActions: '' });
  const [drafting, setDrafting] = useState(false);
  const [draft, setDraft] = useState<string | null>(null);

  const [content, setContent] = useState('');
  const [method, setMethod] = useState<ContactMethod>('MESSAGE');
  const [scheduledAt, setScheduledAt] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [escalating, setEscalating] = useState(false);
  const [resolving, setResolving] = useState(false);
  const [resolveBusy, setResolveBusy] = useState(false);

  useEffect(() => {
    const el = ref.current;
    if (el && !el.open) el.showModal();
    return () => el?.close();
  }, []);

  // 카드를 처음 열면 열람 처리 SUBMITTED → TEACHER_REVIEWING
  useEffect(() => {
    if (complaint?.status === 'SUBMITTED' && !acked.current) {
      acked.current = true;
      complaintApi.acknowledge(complaint.id).then(setComplaint).catch(() => {});
    }
    // 학부모 희망 연락 방법을 기본값으로 (한 번만)
    if (complaint?.summaryCard && !methodInit.current) {
      methodInit.current = true;
      setMethod(complaint.summaryCard.preferredContact.methods[0] ?? 'MESSAGE');
    }
  }, [complaint, setComplaint]);

  async function makeDraft() {
    setDrafting(true);
    setError(null);
    try {
      const d = await complaintApi.createReplyDraft(complaintId, memo);
      setDraft(d.draft);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setDrafting(false);
    }
  }

  async function send() {
    setSending(true);
    setError(null);
    setFieldErrors({});
    try {
      await complaintApi.reply(complaintId, {
        content,
        contactMethod: method,
        scheduledAt: method === 'MESSAGE' ? null : localToKstIso(scheduledAt),
      });
      await reload();
    } catch (e) {
      setError((e as Error).message);
      if (e instanceof ApiError && e.details.fields) setFieldErrors(e.details.fields as Record<string, string>);
    } finally {
      setSending(false);
    }
  }

  async function resolve() {
    setResolveBusy(true);
    try {
      setComplaint(await complaintApi.resolve(complaintId));
      setResolving(false);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setResolveBusy(false);
    }
  }

  const c = complaint;
  const card = c?.summaryCard;
  const canReply = c?.status === 'TEACHER_REVIEWING' && !c.teacherReply;
  const canEscalate = !!c && !c.escalation && ['SUBMITTED', 'TEACHER_REVIEWING', 'IN_CONVERSATION'].includes(c.status);
  const canResolve = !!c && ['IN_CONVERSATION', 'ESCALATED'].includes(c.status);
  const pref = card?.preferredContact;

  return (
    <dialog
      ref={ref}
      className="drawer"
      aria-labelledby="drawer-title"
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
      onClick={(e) => e.target === ref.current && onClose()}
    >
      <div className="drawer__panel">
        <header className="drawer__head">
          <div>
            <h2 id="drawer-title" className="drawer__title">학부모 민원</h2>
            {c && (
              <p className="drawer__sub tnum">
                {senderLabel(c.student.name, c.student.studentNumber)} · {c.parent.name}
                {c.submittedAt && ` · ${formatDateTime(c.submittedAt)} 접수`}
              </p>
            )}
          </div>
          {c && <StatusPill status={c.status} viewerRole="TEACHER" />}
          <button type="button" className="icon-btn" onClick={onClose} aria-label="닫기">
            <CloseIcon />
          </button>
        </header>

        <div className="drawer__body">
          {!c && !loadError && <p className="list-state">불러오는 중…</p>}
          {loadError && <p className="inline-error" role="alert">{loadError}</p>}

          {c && card && (
            <>
              <p className="callout callout--muted">
                학부모님 원문은 보이지 않아요. AI가 감정 표현을 덜어 내고 있었던 일과 요청 위주로 정리한 카드입니다.
              </p>
              <SummaryCardView card={card} />

              {c.escalation && (
                <section className="panel panel--warn">
                  <h3 className="panel__title">민원대응팀 도움 요청</h3>
                  <p className="panel__meta">
                    {STAGE_LABEL[c.escalation.stage]} · {ESC_STATUS_LABEL[c.escalation.status]} · {formatDateTime(c.escalation.requestedAt)}
                    {c.escalation.assignee && ` · 담당 ${c.escalation.assignee.name}`}
                  </p>
                  <div className="chips">
                    {c.escalation.reasonTypes.map((r) => (
                      <span key={r} className="pill pill--warn">{REASON_LABEL[r].label}</span>
                    ))}
                  </div>
                  <p className="panel__text">{c.escalation.description}</p>
                </section>
              )}

              {c.teacherReply && (
                <section className="panel panel--ok">
                  <h3 className="panel__title">보낸 연락</h3>
                  <p className="panel__meta">
                    {CONTACT_LABEL[c.teacherReply.contactMethod]}
                    {c.teacherReply.scheduledAt && ` · ${formatDateTime(c.teacherReply.scheduledAt)} 예약`} · {formatDateTime(c.teacherReply.sentAt)} 보냄
                  </p>
                  <p className="panel__text">{c.teacherReply.content}</p>
                  <p className="panel__hint">이후 대화는 채팅방에서 이어가세요.</p>
                </section>
              )}

              {canReply && (
                <>
                  <section className="panel">
                    <div className="panel__step">
                      <span className="step-no">1</span>
                      <h3 className="panel__title">입장 정리 <span className="pill pill--accent">AI 도우미</span></h3>
                    </div>
                    <p className="panel__hint">짧은 메모만 적어도 정중한 답변 초안으로 다듬어 드려요. 초안은 저장만 되고 바로 보내지지 않아요.</p>
                    <label className="field">
                      <span className="field__label">당시 상황</span>
                      <textarea className="input textarea" rows={2} placeholder="예: 2교시에 학칙대로 휴대폰 보관" value={memo.situation} onChange={(e) => setMemo({ ...memo, situation: e.target.value })} />
                    </label>
                    <label className="field">
                      <span className="field__label">그렇게 한 이유</span>
                      <textarea className="input textarea" rows={2} placeholder="예: 학생생활규정 12조" value={memo.reason} onChange={(e) => setMemo({ ...memo, reason: e.target.value })} />
                    </label>
                    <label className="field">
                      <span className="field__label">앞으로의 조치</span>
                      <textarea className="input textarea" rows={2} placeholder="예: 긴급 시 교무실 번호 안내, 보관 시간은 학년회의에서 검토" value={memo.nextActions} onChange={(e) => setMemo({ ...memo, nextActions: e.target.value })} />
                    </label>
                    <div className="panel__actions">
                      <button type="button" className="btn btn--soft btn--sm" onClick={makeDraft} disabled={drafting}>
                        {drafting ? '정리하는 중…' : draft ? '초안 다시 만들기' : '초안 만들기'}
                      </button>
                    </div>
                    {draft && (
                      <div className="draft">
                        <p className="draft__text">{draft}</p>
                        <button type="button" className="btn btn--sm" onClick={() => setContent(draft)}>
                          이 초안으로 답변 쓰기
                        </button>
                      </div>
                    )}
                  </section>

                  <section className="panel">
                    <div className="panel__step">
                      <span className="step-no">2</span>
                      <h3 className="panel__title">학부모에게 보낼 연락</h3>
                    </div>
                    {pref && (
                      <p className="panel__hint">
                        학부모님 희망: {pref.methods.map((m) => CONTACT_LABEL[m]).join(', ')}
                        {pref.timeWindow ? ` · ${pref.timeWindow.from}~${pref.timeWindow.to}` : ' · 시간 무관'}
                      </p>
                    )}
                    <div className="seg seg--sm" role="radiogroup" aria-label="연락 방법">
                      {METHODS.map((m) => (
                        <button key={m} type="button" role="radio" aria-checked={method === m} className="seg__btn" onClick={() => setMethod(m)}>
                          {CONTACT_LABEL[m]}
                        </button>
                      ))}
                    </div>
                    <p className="field__hint">{METHOD_HINT[method]}</p>
                    {method !== 'MESSAGE' && (
                      <label className="field">
                        <span className="field__label">{method === 'CALL' ? '안심전화 시각' : '면담 시각'}</span>
                        <input type="datetime-local" className="input tnum" value={scheduledAt} onChange={(e) => setScheduledAt(e.target.value)} />
                        {fieldErrors.scheduledAt && <p className="field__error">{fieldErrors.scheduledAt}</p>}
                      </label>
                    )}
                    <label className="field">
                      <span className="field__label">보낼 메시지</span>
                      <textarea className="input textarea" rows={7} value={content} maxLength={2100} placeholder="안녕하세요, 담임 ○○○입니다. …" onChange={(e) => setContent(e.target.value)} />
                      <span className={`field__count tnum ${content.length > 2000 ? 'is-over' : ''}`}>{content.length.toLocaleString()}/2,000</span>
                      {fieldErrors.content && <p className="field__error">{fieldErrors.content}</p>}
                    </label>
                  </section>
                </>
              )}

              {error && <p className="inline-error" role="alert">{error}</p>}
            </>
          )}
        </div>

        {c && (canReply || canEscalate || canResolve) && (
          <footer className="drawer__foot">
            {canEscalate && (
              <button type="button" className="btn btn--ghost btn--warn-text" onClick={() => setEscalating(true)}>
                민원 대응팀 도움 요청
              </button>
            )}
            <span className="drawer__spacer" />
            {canResolve && (
              <button type="button" className="btn btn--ghost" onClick={() => setResolving(true)}>
                처리 종료
              </button>
            )}
            {canReply && (
              <button type="button" className="btn" onClick={send} disabled={sending || !content.trim() || content.length > 2000 || (method !== 'MESSAGE' && !scheduledAt)}>
                {sending ? '보내는 중…' : '학부모님께 보내기'}
              </button>
            )}
          </footer>
        )}
      </div>

      {escalating && c && (
        <EscalationModal complaintId={c.id} onClose={() => setEscalating(false)} onSubmitted={() => void reload()} />
      )}
      {resolving && (
        <ConfirmModal
          title="민원 처리 종료"
          message="이 민원을 처리 완료로 바꿉니다. 채팅방 대화는 그대로 남아요."
          confirmLabel="처리 종료"
          busy={resolveBusy}
          onConfirm={resolve}
          onClose={() => setResolving(false)}
        />
      )}
    </dialog>
  );
}
