import Modal from './Modal';

interface Props {
  title: string;
  message: string;
  confirmLabel: string;
  busy?: boolean;
  error?: string | null;
  onConfirm: () => void;
  onClose: () => void;
}

export default function ConfirmModal({ title, message, confirmLabel, busy, error, onConfirm, onClose }: Props) {
  return (
    <Modal
      title={title}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn btn--ghost" onClick={onClose}>취소</button>
          <button type="button" className="btn btn--danger" onClick={onConfirm} disabled={busy}>
            {busy ? '처리 중…' : confirmLabel}
          </button>
        </>
      }
    >
      <p className="confirm__text">{message}</p>
      {error && <p className="inline-error" role="alert">{error}</p>}
    </Modal>
  );
}
