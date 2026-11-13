import { useState } from 'react';
import { chatApi, type ChatRoomDetail } from '../api';
import Modal from './Modal';

interface Props {
  room: ChatRoomDetail;
  isTeacher: boolean;
  onClose: () => void;
  onSaved: (patch: Partial<ChatRoomDetail>) => void;
}

/** 채팅방 '설정' — PATCH /chat/rooms/{roomId}/settings. 방별 대화 가능 시간은 교사만. */
export default function RoomSettingsModal({ room, isTeacher, onClose, onSaved }: Props) {
  const [muted, setMuted] = useState(!!room.muted);
  const [override, setOverride] = useState(false);
  const [from, setFrom] = useState(room.availableHours?.from ?? '09:00');
  const [to, setTo] = useState(room.availableHours?.to ?? '18:00');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canOverride = isTeacher && room.type !== 'NOTICE';
  const invalid = override && from >= to;

  async function save() {
    setSaving(true);
    setError(null);
    try {
      const updated = await chatApi.updateRoomSettings(room.id, {
        muted,
        ...(canOverride && override ? { availableHoursOverride: { from, to } } : {}),
      });
      onSaved({ muted: updated.muted });
      onClose();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSaving(false);
    }
  }

  async function resetHours() {
    setSaving(true);
    setError(null);
    try {
      await chatApi.updateRoomSettings(room.id, { availableHoursOverride: null });
      onClose();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      title="채팅방 설정"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn btn--ghost" onClick={onClose}>취소</button>
          <button type="button" className="btn" onClick={save} disabled={saving || invalid}>
            {saving ? '저장 중…' : '저장'}
          </button>
        </>
      }
    >
      <div className="form">
        {error && <p className="inline-error" role="alert">{error}</p>}
        <label className="field field--switch">
          <span className="field__label">이 방 알림 끄기</span>
          <input type="checkbox" role="switch" className="switch" checked={muted} onChange={(e) => setMuted(e.target.checked)} />
        </label>

        {canOverride && (
          <fieldset className="field">
            <legend className="field__label">이 방의 대화 가능 시간</legend>
            <label className="check">
              <input type="checkbox" checked={override} onChange={(e) => setOverride(e.target.checked)} />
              내 채팅 설정과 다르게 정하기
            </label>
            {override && (
              <div className="field__row">
                <input type="time" className="input tnum" value={from} onChange={(e) => setFrom(e.target.value)} aria-label="시작 시간" />
                <span aria-hidden="true">~</span>
                <input type="time" className="input tnum" value={to} onChange={(e) => setTo(e.target.value)} aria-label="종료 시간" />
              </div>
            )}
            {invalid && <p className="field__error">시작 시간이 종료 시간보다 빨라야 합니다.</p>}
            <button type="button" className="link-btn" onClick={resetHours} disabled={saving}>
              내 채팅 설정 시간으로 되돌리기
            </button>
          </fieldset>
        )}
      </div>
    </Modal>
  );
}
