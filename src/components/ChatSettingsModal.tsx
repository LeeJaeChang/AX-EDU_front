import { useEffect, useState } from 'react';
import { chatApi, type ChatSetting, type DayOfWeek, type Role } from '../api';
import Modal from './Modal';

const DAYS: { v: DayOfWeek; label: string }[] = [
  { v: 'MON', label: '월' },
  { v: 'TUE', label: '화' },
  { v: 'WED', label: '수' },
  { v: 'THU', label: '목' },
  { v: 'FRI', label: '금' },
  { v: 'SAT', label: '토' },
  { v: 'SUN', label: '일' },
];

const OUTSIDE_ROLES: { v: Role; label: string }[] = [
  { v: 'TEACHER', label: '교사' },
  { v: 'PARENT', label: '학부모' },
  { v: 'STUDENT', label: '학생' },
];

const toggle = <T,>(list: T[], v: T) => (list.includes(v) ? list.filter((x) => x !== v) : [...list, v]);

/** 내 채팅 설정 — GET·PUT /me/chat-settings. 대화 가능 시간은 교사만 바꿀 수 있다. */
export default function ChatSettingsModal({ isTeacher, onClose }: { isTeacher: boolean; onClose: () => void }) {
  const [form, setForm] = useState<ChatSetting | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    chatApi.getChatSettings().then(setForm).catch((e: Error) => setError(e.message));
  }, []);

  const set = <K extends keyof ChatSetting>(k: K, v: ChatSetting[K]) => setForm((f) => (f ? { ...f, [k]: v } : f));
  const invalidHours = !!form && form.availableHours.from >= form.availableHours.to;

  async function save() {
    if (!form || invalidHours) return;
    setSaving(true);
    setError(null);
    try {
      await chatApi.saveChatSettings(form);
      onClose();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      title="내 채팅 설정"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn btn--ghost" onClick={onClose}>취소</button>
          <button type="button" className="btn" onClick={save} disabled={!form || saving || invalidHours}>
            {saving ? '저장 중…' : '저장'}
          </button>
        </>
      }
    >
      {!form && !error && <p className="list-state">불러오는 중…</p>}
      {error && <p className="inline-error" role="alert">{error}</p>}
      {form && (
        <div className="form">
          {isTeacher && (
            <>
              <fieldset className="field">
                <legend className="field__label">대화 가능 시간</legend>
                <div className="field__row">
                  <input
                    type="time"
                    className="input tnum"
                    value={form.availableHours.from}
                    onChange={(e) => set('availableHours', { ...form.availableHours, from: e.target.value })}
                    aria-label="시작 시간"
                  />
                  <span aria-hidden="true">~</span>
                  <input
                    type="time"
                    className="input tnum"
                    value={form.availableHours.to}
                    onChange={(e) => set('availableHours', { ...form.availableHours, to: e.target.value })}
                    aria-label="종료 시간"
                  />
                </div>
                {invalidHours && <p className="field__error">시작 시간이 종료 시간보다 빨라야 합니다.</p>}
                <p className="field__hint">학부모·학생은 이 시간 밖에 메시지를 보낼 수 없습니다. 민원 요약 카드는 시간과 관계없이 접수되고, 알림만 다음 가능 시간에 받습니다.</p>
              </fieldset>

              <fieldset className="field">
                <legend className="field__label">대화 가능 요일</legend>
                <div className="chips">
                  {DAYS.map((d) => (
                    <button
                      key={d.v}
                      type="button"
                      className="chip-btn"
                      aria-pressed={form.availableDays.includes(d.v)}
                      onClick={() => set('availableDays', toggle(form.availableDays, d.v))}
                    >
                      {d.label}
                    </button>
                  ))}
                </div>
              </fieldset>

              <fieldset className="field">
                <legend className="field__label">시간 외에도 받을 상대</legend>
                <div className="chips">
                  {OUTSIDE_ROLES.map((r) => (
                    <button
                      key={r.v}
                      type="button"
                      className="chip-btn"
                      aria-pressed={form.allowOutsideHoursFor.includes(r.v)}
                      onClick={() => set('allowOutsideHoursFor', toggle(form.allowOutsideHoursFor, r.v))}
                    >
                      {r.label}
                    </button>
                  ))}
                </div>
              </fieldset>

              <label className="field">
                <span className="field__label">입력창 안내 문구</span>
                <input
                  className="input"
                  value={form.outsideHoursNotice}
                  maxLength={100}
                  onChange={(e) => set('outsideHoursNotice', e.target.value)}
                />
                <span className="field__hint">{'{from}'}, {'{to}'}는 대화 가능 시간으로 바뀝니다.</span>
              </label>
            </>
          )}

          <label className="field field--switch">
            <span className="field__label">푸시 알림</span>
            <input
              type="checkbox"
              role="switch"
              className="switch"
              checked={form.pushEnabled}
              onChange={(e) => set('pushEnabled', e.target.checked)}
            />
          </label>
        </div>
      )}
    </Modal>
  );
}
