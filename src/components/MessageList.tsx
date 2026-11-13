import { Fragment } from 'react';
import type { Attachment, Role } from '../api';
import ComplaintMessage from './complaint/ComplaintMessage';
import type { ViewMessage } from '../hooks/useMessages';
import { dayKey, formatDateDivider, formatFileSize, formatTime, senderLabel } from '../lib/format';
import Avatar from './Avatar';
import { FileIcon } from './Icons';

interface Props {
  messages: ViewMessage[];
  myId: string;
  viewerRole: Role;
  onRetry: (m: ViewMessage) => void;
  onDiscard: (m: ViewMessage) => void;
  onOpenComplaint?: (id: string) => void;
}

/** 오래된 순으로 그리며 KST 날짜가 바뀔 때 구분선을 넣는다 (명세서 5.1) */
export default function MessageList({ messages, myId, viewerRole, onRetry, onDiscard, onOpenComplaint }: Props) {
  return (
    <ol className="messages">
      {messages.map((m, i) => {
        const prev = messages[i - 1];
        const newDay = !prev || dayKey(prev.createdAt) !== dayKey(m.createdAt);
        return (
          <Fragment key={m.clientMessageId ?? m.id}>
            {newDay && (
              <li className="date-divider">
                <span>{formatDateDivider(m.createdAt)}</span>
              </li>
            )}
            <MessageRow m={m} mine={m.sender?.id === myId} viewerRole={viewerRole} onRetry={onRetry} onDiscard={onDiscard} onOpenComplaint={onOpenComplaint} />
          </Fragment>
        );
      })}
    </ol>
  );
}

function MessageRow({
  m,
  mine,
  viewerRole,
  onRetry,
  onDiscard,
  onOpenComplaint,
}: {
  m: ViewMessage;
  mine: boolean;
  viewerRole: Role;
  onRetry: (m: ViewMessage) => void;
  onDiscard: (m: ViewMessage) => void;
  onOpenComplaint?: (id: string) => void;
}) {
  if (m.type === 'SYSTEM' || !m.sender) {
    return (
      <li className="system-msg">
        <span>{m.content}</span>
      </li>
    );
  }

  const meta = (
    <div className="msg__meta">
      {m.status === 'sending' && <span className="msg__status">전송 중</span>}
      {m.status === 'failed' && (
        <span className="msg__failed">
          <button type="button" className="link-btn" onClick={() => onRetry(m)}>재전송</button>
          <button type="button" className="link-btn link-btn--muted" onClick={() => onDiscard(m)}>삭제</button>
        </span>
      )}
      {!m.status && mine && !m.readByCounterpart && (
        <span className="msg__unread" aria-label="상대가 아직 읽지 않음">1</span>
      )}
      {!m.status && (
        <time className="msg__time tnum" dateTime={m.createdAt}>
          {formatTime(m.createdAt)}
        </time>
      )}
    </div>
  );

  const isComplaint = m.type === 'COMPLAINT_CARD' || m.type === 'COMPLAINT_REPLY';
  const bubble = isComplaint ? (
    <div className={`bubble bubble--card ${m.type === 'COMPLAINT_CARD' ? 'bubble--wide' : ''}`}>
      <ComplaintMessage m={m} viewerRole={viewerRole} onOpenComplaint={onOpenComplaint} />
    </div>
  ) : (
    <div className={`bubble ${mine ? 'bubble--mine' : 'bubble--theirs'}`}>
      {!!m.attachments?.length && <Attachments items={m.attachments} />}
      {m.content && <p className="bubble__text">{m.content}</p>}
    </div>
  );

  if (mine) {
    return (
      <li className={`msg msg--mine ${m.status === 'failed' ? 'is-failed' : ''} ${isComplaint ? 'msg--complaint' : ''}`}>
        {meta}
        {bubble}
        {m.status === 'failed' && m.errorMessage && (
          <p className="msg__error" role="alert">{m.errorMessage}</p>
        )}
      </li>
    );
  }

  return (
    <li className={`msg msg--theirs ${isComplaint ? 'msg--complaint' : ''}`}>
      <Avatar
        name={m.sender.name}
        imageUrl={m.sender.profileImageUrl}
        variant={m.sender.role === 'NOTICE' ? 'notice' : 'placeholder'}
        size={48}
      />
      <div className="msg__col">
        <span className="msg__sender">{senderLabel(m.sender.name, m.sender.studentNumber)}</span>
        <div className="msg__line">
          {bubble}
          {meta}
        </div>
      </div>
    </li>
  );
}

function Attachments({ items }: { items: Attachment[] }) {
  return (
    <div className="attachments">
      {items.map((a) =>
        a.mimeType.startsWith('image/') ? (
          <a key={a.id} href={a.url} target="_blank" rel="noreferrer" className="attachment-img">
            <img src={a.thumbnailUrl ?? a.url} alt={a.name} loading="lazy" />
          </a>
        ) : (
          <a key={a.id} href={a.url} target="_blank" rel="noreferrer" className="attachment-file" download={a.name}>
            <FileIcon width={18} height={18} />
            <span className="attachment-file__name">{a.name}</span>
            <span className="attachment-file__size tnum">{formatFileSize(a.size)}</span>
          </a>
        ),
      )}
    </div>
  );
}
