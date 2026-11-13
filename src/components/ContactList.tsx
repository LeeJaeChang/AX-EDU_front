import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError, chatApi, type Contact } from '../api';
import { useDebounced } from '../hooks/useDebounced';
import Avatar from './Avatar';

const ROLE_LABEL: Record<string, string> = { STUDENT: '학생', PARENT: '학부모', TEACHER: '교사' };
type RoleFilter = '' | 'STUDENT' | 'PARENT' | 'TEACHER';

/** '새 대화' 탭: GET /chat/contacts → POST /chat/rooms (409면 기존 방으로 이동) */
export default function ContactList({ q }: { q: string }) {
  const navigate = useNavigate();
  const query = useDebounced(q);
  const [role, setRole] = useState<RoleFilter>('');
  const [contacts, setContacts] = useState<Contact[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pendingId, setPendingId] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    setContacts(null);
    chatApi
      .listContacts({ q: query || undefined, role: role || undefined })
      .then((list) => alive && setContacts(list))
      .catch((e: Error) => alive && setError(e.message));
    return () => {
      alive = false;
    };
  }, [query, role]);

  async function start(c: Contact) {
    if (c.existingRoomId) return navigate(`/chat/${c.existingRoomId}`);
    setPendingId(c.id);
    try {
      const room = await chatApi.createRoom(c.id);
      navigate(`/chat/${room.id}`);
    } catch (e) {
      if (e instanceof ApiError && e.code === 'ROOM_ALREADY_EXISTS') {
        navigate(`/chat/${String(e.details.roomId)}`);
      } else {
        setError((e as Error).message);
      }
    } finally {
      setPendingId(null);
    }
  }

  return (
    <div className="contacts">
      <div className="chips" role="group" aria-label="역할 필터">
        {(['', 'STUDENT', 'PARENT', 'TEACHER'] as const).map((r) => (
          <button
            key={r || 'all'}
            type="button"
            className="chip-btn"
            aria-pressed={role === r}
            onClick={() => setRole(r)}
          >
            {r ? ROLE_LABEL[r] : '전체'}
          </button>
        ))}
      </div>

      {error && <p className="inline-error" role="alert">{error}</p>}
      {!contacts && !error && <p className="list-state">불러오는 중…</p>}
      {contacts?.length === 0 && <p className="list-state">대화할 수 있는 상대가 없습니다.</p>}

      <ul className="room-list">
        {contacts?.map((c) => (
          <li key={c.id}>
            <button type="button" className="room-item" onClick={() => start(c)} disabled={pendingId === c.id}>
              <Avatar name={c.name} imageUrl={c.profileImageUrl} size={56} />
              <div className="room-item__body">
                <div className="room-item__title-row">
                  <strong className="room-item__title">
                    {c.name} {c.studentNumber} {ROLE_LABEL[c.role] && `[${ROLE_LABEL[c.role]}]`}
                  </strong>
                </div>
                <p className="room-item__preview">
                  {c.existingRoomId ? '눌러서 대화를 이어가세요.' : '눌러서 새 대화를 시작하세요.'}
                </p>
              </div>
              <span className={c.existingRoomId ? 'pill pill--neutral' : 'pill pill--accent'}>
                {pendingId === c.id ? '여는 중…' : c.existingRoomId ? '대화 중' : '대화 시작'}
              </span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
