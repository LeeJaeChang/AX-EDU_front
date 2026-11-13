import { useRef, useState, type KeyboardEvent } from 'react';
import { chatApi, type Attachment } from '../api';
import { formatFileSize } from '../lib/format';
import { ArrowUpIcon, CloseIcon, FileIcon, PlusIcon } from './Icons';

const MAX_LEN = 2000;
const ACCEPT = 'image/*,.pdf,.hwp,.docx';

interface Props {
  placeholder: string;
  disabled: boolean;
  onSend: (out: { content: string; attachments: Attachment[] }) => Promise<unknown>;
  /** + 첨부 버튼 표시 (기본 true) */
  allowAttach?: boolean;
  /** 전송 실패 시 입력 내용을 되살린다 (채팅은 말풍선에서 재전송하므로 false) */
  restoreOnError?: boolean;
}

interface PendingFile {
  key: string;
  name: string;
  size: number;
  uploaded?: Attachment;
  error?: string;
}

/** 입력창: + 첨부(POST /files) · ↑ 전송. Enter 전송, Shift+Enter 줄바꿈, 한글 조합 중 Enter는 무시. */
export default function Composer({ placeholder, disabled, onSend, allowAttach = true, restoreOnError = false }: Props) {
  const [text, setText] = useState('');
  const [files, setFiles] = useState<PendingFile[]>([]);
  const fileInput = useRef<HTMLInputElement>(null);
  const textarea = useRef<HTMLTextAreaElement>(null);

  const uploading = files.some((f) => !f.uploaded && !f.error);
  const ready = files.filter((f) => f.uploaded).map((f) => f.uploaded!);
  const canSubmit = !disabled && !uploading && (text.trim().length > 0 || ready.length > 0) && text.length <= MAX_LEN;

  async function pick(list: FileList | null) {
    if (!list) return;
    for (const file of Array.from(list)) {
      const key = `${file.name}-${file.size}-${Math.random()}`;
      setFiles((prev) => [...prev, { key, name: file.name, size: file.size }]);
      try {
        const up = await chatApi.uploadFile(file);
        const att: Attachment = { id: up.id, name: up.name, mimeType: up.mimeType, size: up.size, url: up.url, thumbnailUrl: up.mimeType.startsWith('image/') ? up.url : null };
        setFiles((prev) => prev.map((f) => (f.key === key ? { ...f, uploaded: att } : f)));
      } catch (e) {
        setFiles((prev) => prev.map((f) => (f.key === key ? { ...f, error: (e as Error).message } : f)));
      }
    }
    if (fileInput.current) fileInput.current.value = '';
  }

  function submit() {
    if (!canSubmit) return;
    const content = text.trim();
    setText('');
    setFiles([]);
    const kept = files;
    onSend({ content, attachments: ready }).catch(() => {
      if (restoreOnError) {
        setText(content);
        setFiles(kept);
      }
      /* 채팅은 말풍선에서 재전송으로 처리 */
    });
    textarea.current?.focus();
  }

  function onKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      submit();
    }
  }

  return (
    <form
      className={`composer ${disabled ? 'is-disabled' : ''}`}
      onSubmit={(e) => {
        e.preventDefault();
        submit();
      }}
    >
      {files.length > 0 && (
        <ul className="composer__files">
          {files.map((f) => (
            <li key={f.key} className={`file-chip ${f.error ? 'is-error' : ''}`}>
              <FileIcon width={16} height={16} />
              <span className="file-chip__name">{f.name}</span>
              <span className="file-chip__meta tnum">
                {f.error ? f.error : f.uploaded ? formatFileSize(f.size) : '올리는 중…'}
              </span>
              <button
                type="button"
                className="icon-btn icon-btn--sm"
                aria-label={`${f.name} 빼기`}
                onClick={() => setFiles((prev) => prev.filter((x) => x.key !== f.key))}
              >
                <CloseIcon width={14} height={14} />
              </button>
            </li>
          ))}
        </ul>
      )}

      <textarea
        ref={textarea}
        className="composer__input"
        placeholder={placeholder}
        value={text}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={onKeyDown}
        disabled={disabled}
        rows={2}
        aria-label="메시지 입력"
      />

      <div className="composer__bar">
        {allowAttach && (
          <button
            type="button"
            className="round-btn round-btn--outline"
            aria-label="파일 첨부"
            disabled={disabled}
            onClick={() => fileInput.current?.click()}
          >
            <PlusIcon width={22} height={22} />
          </button>
        )}
        <input ref={fileInput} type="file" accept={ACCEPT} multiple hidden onChange={(e) => pick(e.target.files)} />

        {text.length > MAX_LEN * 0.9 && (
          <span className={`composer__count tnum ${text.length > MAX_LEN ? 'is-over' : ''}`}>
            {text.length.toLocaleString()}/{MAX_LEN.toLocaleString()}
          </span>
        )}

        <button type="submit" className="round-btn round-btn--send" aria-label="보내기" disabled={!canSubmit}>
          <ArrowUpIcon width={22} height={22} />
        </button>
      </div>
    </form>
  );
}
