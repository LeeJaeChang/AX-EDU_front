import { useState } from 'react';
import { ApiError, complaintApi, type ComplaintTag, type ContactMethod, type SituationItem, type SummaryCard, type SummaryCardResult } from '../../api';
import { CONTACT_LABEL, TAG_LABEL } from '../../lib/complaint';
import { CloseIcon, PlusIcon } from '../Icons';
import Modal from '../Modal';

interface Props {
  complaintId: string;
  card: SummaryCard;
  onClose: () => void;
  onSaved: (card: SummaryCardResult) => void;
}

const TAGS = Object.keys(TAG_LABEL) as ComplaintTag[];
const METHODS = Object.keys(CONTACT_LABEL) as ContactMethod[];

/** 카드 항목 직접 수정 — PATCH /complaints/{id}/summary-card. 규정·출처 문구는 고칠 수 없다. */
export default function CardEditorModal({ complaintId, card, onClose, onSaved }: Props) {
  const [tags, setTags] = useState<ComplaintTag[]>(card.basicInfo.tags);
  const [situation, setSituation] = useState<SituationItem[]>(card.situation);
  const [concerns, setConcerns] = useState<string[]>(card.concerns);
  const [requests, setRequests] = useState<string[]>(card.requests);
  const [methods, setMethods] = useState<ContactMethod[]>(card.preferredContact.methods);
  const [useWindow, setUseWindow] = useState(!!card.preferredContact.timeWindow);
  const [from, setFrom] = useState(card.preferredContact.timeWindow?.from ?? '12:00');
  const [to, setTo] = useState(card.preferredContact.timeWindow?.to ?? '13:00');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const toggle = <T,>(list: T[], v: T) => (list.includes(v) ? list.filter((x) => x !== v) : [...list, v]);

  async function save() {
    setSaving(true);
    setError(null);
    setFieldErrors({});
    try {
      const saved = await complaintApi.editSummaryCard(complaintId, card.version, {
        tags,
        situation: situation.filter((s) => s.what.trim()).map((s) => ({ ...s, when: s.when.trim() || '—', where: s.where.trim() || '—' })),
        concerns: concerns.map((c) => c.trim()).filter(Boolean),
        requests: requests.map((r) => r.trim()).filter(Boolean),
        preferredContact: { methods, timeWindow: useWindow ? { from, to } : null },
      });
      onSaved(saved);
      onClose();
    } catch (e) {
      setError((e as Error).message);
      if (e instanceof ApiError && e.details.fields) setFieldErrors(e.details.fields as Record<string, string>);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      title="요약 카드 직접 고치기"
      onClose={onClose}
      wide
      footer={
        <>
          <button type="button" className="btn btn--ghost" onClick={onClose}>취소</button>
          <button type="button" className="btn" onClick={save} disabled={saving}>
            {saving ? '저장 중…' : `저장 (v${card.version + 1})`}
          </button>
        </>
      }
    >
      <div className="form">
        {error && <p className="inline-error" role="alert">{error}</p>}

        <fieldset className="field">
          <legend className="field__label">민원 유형</legend>
          <div className="chips">
            {TAGS.map((t) => (
              <button key={t} type="button" className="chip-btn" aria-pressed={tags.includes(t)} onClick={() => setTags(toggle(tags, t))}>
                {TAG_LABEL[t]}
              </button>
            ))}
          </div>
        </fieldset>

        <fieldset className="field">
          <legend className="field__label">상황 (시간순)</legend>
          <p className="field__hint">카드에는 앞에 ‘{card.attribution}’가 붙어요.</p>
          <ol className="edit-steps">
            {situation.map((s, i) => (
              <li key={i} className="edit-step">
                <span className="edit-step__no tnum">{i + 1}</span>
                <div className="edit-step__fields">
                  <div className="field__row">
                    <input className="input" placeholder="언제 (예: 9월 10일 2교시)" value={s.when === '—' ? '' : s.when} onChange={(e) => setSituation(situation.map((x, j) => (j === i ? { ...x, when: e.target.value } : x)))} aria-label={`${i + 1}번 상황 시각`} />
                    <input className="input" placeholder="어디서 (예: 교실)" value={s.where === '—' ? '' : s.where} onChange={(e) => setSituation(situation.map((x, j) => (j === i ? { ...x, where: e.target.value } : x)))} aria-label={`${i + 1}번 상황 장소`} />
                  </div>
                  <input className="input" placeholder="무슨 일이 있었나요" value={s.what} onChange={(e) => setSituation(situation.map((x, j) => (j === i ? { ...x, what: e.target.value } : x)))} aria-label={`${i + 1}번 상황 내용`} />
                </div>
                <button type="button" className="icon-btn icon-btn--sm" aria-label={`${i + 1}번 상황 지우기`} onClick={() => setSituation(situation.filter((_, j) => j !== i))}>
                  <CloseIcon width={14} height={14} />
                </button>
              </li>
            ))}
          </ol>
          <button type="button" className="add-btn" onClick={() => setSituation([...situation, { order: situation.length + 1, when: '', where: '', what: '' }])}>
            <PlusIcon width={16} height={16} /> 상황 추가
          </button>
          {fieldErrors.situation && <p className="field__error">{fieldErrors.situation}</p>}
        </fieldset>

        <ListEditor label="학생에게 미친 영향" items={concerns} onChange={setConcerns} placeholder="예: 급한 상황에 연락할 방법이 없음" />
        <ListEditor label="요청 사항" items={requests} onChange={setRequests} placeholder="예: 보관 시간 단축 검토" error={fieldErrors.requests} />

        <fieldset className="field">
          <legend className="field__label">희망 연락</legend>
          <div className="chips">
            {METHODS.map((m) => (
              <button key={m} type="button" className="chip-btn" aria-pressed={methods.includes(m)} onClick={() => setMethods(toggle(methods, m))}>
                {CONTACT_LABEL[m]}
              </button>
            ))}
          </div>
          <label className="check">
            <input type="checkbox" checked={useWindow} onChange={(e) => setUseWindow(e.target.checked)} />
            연락받기 좋은 시간 정하기
          </label>
          {useWindow && (
            <div className="field__row">
              <input type="time" className="input tnum" value={from} onChange={(e) => setFrom(e.target.value)} aria-label="연락 시작 시간" />
              <span aria-hidden="true">~</span>
              <input type="time" className="input tnum" value={to} onChange={(e) => setTo(e.target.value)} aria-label="연락 끝 시간" />
            </div>
          )}
          {fieldErrors.preferredContact && <p className="field__error">{fieldErrors.preferredContact}</p>}
        </fieldset>

        <p className="field__hint">관련 규정과 ‘{card.attribution}’ 문구는 바꿀 수 없어요.</p>
      </div>
    </Modal>
  );
}

function ListEditor({ label, items, onChange, placeholder, error }: { label: string; items: string[]; onChange: (v: string[]) => void; placeholder: string; error?: string }) {
  return (
    <fieldset className="field">
      <legend className="field__label">{label}</legend>
      <ul className="edit-list">
        {items.map((v, i) => (
          <li key={i} className="edit-list__item">
            <input className="input" value={v} placeholder={placeholder} onChange={(e) => onChange(items.map((x, j) => (j === i ? e.target.value : x)))} aria-label={`${label} ${i + 1}`} />
            <button type="button" className="icon-btn icon-btn--sm" aria-label={`${label} ${i + 1} 지우기`} onClick={() => onChange(items.filter((_, j) => j !== i))}>
              <CloseIcon width={14} height={14} />
            </button>
          </li>
        ))}
      </ul>
      <button type="button" className="add-btn" onClick={() => onChange([...items, ''])}>
        <PlusIcon width={16} height={16} /> 항목 추가
      </button>
      {error && <p className="field__error">{error}</p>}
    </fieldset>
  );
}
