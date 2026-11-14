import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { chatApi, complaintApi, realtime, type Complaint } from '../../api';
import { isDrafting } from '../../lib/complaint';
import { formatListTime } from '../../lib/format';
import StatusPill from './StatusPill';

/**
 * 학부모의 첫 화면 '민원하기' (대화 목록 대신).
 * 새 상담 시작 · 이어서 할 상담 · 지난 민원(→ 그 선생님과의 대화)을 한곳에 모은다.
 * 학부모는 선생님 방을 직접 열지 않고, AI 상담을 거쳐 받을 선생님을 정한다.
 */
export default function ParentComplaintEntry() {
  const [items, setItems] = useState<Complaint[] | null>(null);
  // 대화 목록이 없으므로 선생님 답장은 지난 민원 줄의 안 읽은 수로 알린다
  const [unread, setUnread] = useState<Record<string, number>>({});

  const load = useCallback(() => {
    complaintApi
      .list()
      .then((p) => setItems(p.items))
      .catch(() => setItems([]));
  }, []);

  const loadUnread = useCallback(() => {
    chatApi
      .listRooms({ type: 'DIRECT', limit: 100 })
      .then((p) => setUnread(Object.fromEntries(p.items.map((r) => [r.id, r.unreadCount ?? 0]))))
      .catch(() => {});
  }, []);

  useEffect(() => {
    load();
    loadUnread();
    return realtime.subscribe((e) => {
      if (e.event === 'complaint.status_changed') load();
      if (e.event === 'message.created' || e.event === 'room.unread_changed' || e.event === 'message.read') loadUnread();
    });
  }, [load, loadUnread]);

  const drafts = (items ?? []).filter((c) => isDrafting(c.status));
  const sent = (items ?? []).filter((c) => !isDrafting(c.status));

  return (
    <div className="complaint-entry">
      <div className="complaint-entry__cta">
        <span className="avatar avatar--ai" style={{ width: 44, height: 44 }} aria-hidden="true">AI</span>
        <div className="complaint-entry__text">
          <strong className="complaint-entry__title">마음에 걸리는 일이 있으셨나요?</strong>
          <p className="complaint-entry__sub">
            똑똑 AI 상담사가 먼저 이야기를 듣고, 있었던 일을 함께 확인하며 관련 규정과 법령을 찾아 드려요. 누구에게 전하면 좋을지도 같이 정한 뒤, 요약 카드로 선생님께 전해 드려요.
          </p>
        </div>
        <Link to="/complaint" className="btn">새 상담 시작</Link>
      </div>

      {drafts.length > 0 && (
        <section aria-label="이어서 할 상담">
          <h2 className="complaint-entry__h">이어서 할 상담</h2>
          <ul className="complaint-entry__drafts">
            {drafts.map((c) => (
              <ComplaintRow key={c.id} c={c} to={`/complaint/${c.id}`} action="이어서 하기" />
            ))}
          </ul>
        </section>
      )}

      {sent.length > 0 && (
        <section aria-label="지난 민원">
          <h2 className="complaint-entry__h">지난 민원</h2>
          <ul className="complaint-entry__drafts">
            {sent.map((c) => (
              <ComplaintRow
                key={c.id}
                c={c}
                unread={c.roomId ? unread[c.roomId] : 0}
                to={c.roomId ? `/chat/${c.roomId}` : `/complaint/${c.id}`}
                action={c.roomId ? `${c.teacher?.name ?? ''} 선생님과의 대화` : '상담 보기'}
              />
            ))}
          </ul>
        </section>
      )}

      {items && items.length === 0 && <p className="list-state">아직 나눈 상담이 없어요.</p>}
    </div>
  );
}

function ComplaintRow({ c, to, action, unread = 0 }: { c: Complaint; to: string; action: string; unread?: number }) {
  return (
    <li>
      <Link to={to} className="draft-item">
        <StatusPill status={c.status} viewerRole="PARENT" />
        <span className="draft-item__text">{c.originalText}</span>
        <time className="draft-item__time tnum" dateTime={c.createdAt}>{formatListTime(c.createdAt)}</time>
        {unread > 0 && (
          <span className="unread tnum" aria-label={`안 읽은 메시지 ${unread}개`}>
            {unread > 99 ? '99+' : unread}
          </span>
        )}
        <span className="draft-item__go">{action}</span>
      </Link>
    </li>
  );
}
