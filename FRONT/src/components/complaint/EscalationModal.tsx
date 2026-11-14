import { useState } from 'react';
import { ApiError, complaintApi, type Escalation, type EscalationReason } from '../../api';
import { REASON_LABEL } from '../../lib/complaint';
import Modal from '../Modal';

const REASONS = Object.keys(REASON_LABEL) as EscalationReason[];
const MAX = 2000;

interface Props {
  complaintId: string;
  onClose: () => void;
  onSubmitted: (esc: Escalation) => void;
}

/** STEP 5 '민원 대응팀 도움 요청' → 상황 설명 → 제출 (명세서 7.10) */
export default function EscalationModal({ complaintId, onClose, onSubmitted }: Props) {
  const [reasons, setReasons] = useState<EscalationReason[]>([]);
  const [description, setDescription] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const valid = reasons.length > 0 && description.trim().length > 0 && description.length <= MAX;

  async function submit() {
    setSaving(true);
    setError(null);
    setFieldErrors({});
    try {
      const esc = await complaintApi.escalate(complaintId, { reasonTypes: reasons, description });
      onSubmitted(esc);
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
      title="민원 대응팀 도움 요청"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn btn--ghost" onClick={onClose}>취소</button>
          <button type="button" className="btn btn--danger" onClick={submit} disabled={!valid || saving}>
            {saving ? '제출 중…' : '제출'}
          </button>
        </>
      }
    >
      <div className="form">
        <p className="callout">
          요약 카드, AI 규정 판단, 이 방의 대화 기록이 <strong>사건 파일</strong>로 묶여 민원대응팀에 전달돼요. 혼자 감당하지 않으셔도 됩니다.
        </p>
        {error && <p className="inline-error" role="alert">{error}</p>}

        <fieldset className="field">
          <legend className="field__label">도움이 필요한 이유 (여러 개 고를 수 있어요)</legend>
          <div className="reason-grid">
            {REASONS.map((r) => (
              <label key={r} className={`reason ${reasons.includes(r) ? 'is-on' : ''}`}>
                <input
                  type="checkbox"
                  checked={reasons.includes(r)}
                  onChange={() => setReasons(reasons.includes(r) ? reasons.filter((x) => x !== r) : [...reasons, r])}
                />
                <span className="reason__label">{REASON_LABEL[r].label}</span>
                {REASON_LABEL[r].hint && <span className="reason__hint">{REASON_LABEL[r].hint}</span>}
              </label>
            ))}
          </div>
          {fieldErrors.reasonTypes && <p className="field__error">{fieldErrors.reasonTypes}</p>}
        </fieldset>

        <label className="field">
          <span className="field__label">상황 설명 및 민원대응팀 도움 요청사항</span>
          <textarea
            className="input textarea"
            rows={6}
            value={description}
            maxLength={MAX + 100}
            placeholder="예: 답변 후에도 같은 요구가 3회 반복되고, 밤 10시 이후 연락을 요구합니다. 학부모 면담에 함께해 주시면 좋겠습니다."
            onChange={(e) => setDescription(e.target.value)}
          />
          <span className={`field__count tnum ${description.length > MAX ? 'is-over' : ''}`}>
            {description.length.toLocaleString()}/{MAX.toLocaleString()}
          </span>
          {fieldErrors.description && <p className="field__error">{fieldErrors.description}</p>}
        </label>
      </div>
    </Modal>
  );
}
