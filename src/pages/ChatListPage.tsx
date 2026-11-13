import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import ChatSettingsModal from '../components/ChatSettingsModal';
import ContactList from '../components/ContactList';
import { GearIcon, SearchIcon } from '../components/Icons';
import RoomListItem from '../components/RoomListItem';
import { useDebounced } from '../hooks/useDebounced';
import { useMe } from '../hooks/useMe';
import { useRooms } from '../hooks/useRooms';

type Tab = 'rooms' | 'new';

export default function ChatListPage() {
  const me = useMe();
  const [params, setParams] = useSearchParams();
  const tab: Tab = params.get('tab') === 'new' ? 'new' : 'rooms';
  const [q, setQ] = useState('');
  const debouncedQ = useDebounced(q);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const { rooms, loading, error, hasMore, loadMore, reload } = useRooms(tab === 'rooms' ? debouncedQ : '', me.id);

  // 무한 스크롤 (커서 페이지네이션)
  const sentinel = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const el = sentinel.current;
    if (!el || tab !== 'rooms') return;
    const io = new IntersectionObserver((entries) => entries[0].isIntersecting && loadMore());
    io.observe(el);
    return () => io.disconnect();
  }, [loadMore, tab]);

  const switchTab = (t: Tab) => {
    setQ('');
    setParams(t === 'new' ? { tab: 'new' } : {}, { replace: true });
  };

  return (
    <main className="page wrap">
      <header className="list-head">
        <h1 className="page-title">채팅</h1>
        <button type="button" className="btn btn--soft" onClick={() => setSettingsOpen(true)}>
          <GearIcon width={18} height={18} />
          내 채팅 설정
        </button>
      </header>

      <div className="seg" role="tablist" aria-label="채팅 보기">
        <button type="button" role="tab" className="seg__btn" aria-selected={tab === 'rooms'} onClick={() => switchTab('rooms')}>
          대화 목록
        </button>
        <button type="button" role="tab" className="seg__btn" aria-selected={tab === 'new'} onClick={() => switchTab('new')}>
          새 대화
        </button>
      </div>

      <hr className="divider" />

      <label className="search">
        <SearchIcon className="search__icon" width={22} height={22} />
        <input
          type="search"
          className="search__input"
          placeholder={tab === 'rooms' ? '대화 상대를 검색해 보세요.' : '이름이나 학번으로 찾아보세요.'}
          value={q}
          onChange={(e) => setQ(e.target.value)}
          aria-label="대화 상대 검색"
        />
      </label>

      {tab === 'rooms' ? (
        <section role="tabpanel" aria-label="대화 목록">
          {error && (
            <p className="inline-error" role="alert">
              {error} <button type="button" className="link-btn" onClick={reload}>다시 시도</button>
            </p>
          )}
          <ul className="room-list">
            {rooms.map((r) => (
              <RoomListItem key={r.id} room={r} />
            ))}
          </ul>
          {!loading && !error && rooms.length === 0 && (
            <p className="list-state">{debouncedQ ? `'${debouncedQ}'에 맞는 대화가 없습니다.` : '아직 대화가 없습니다. 새 대화를 시작해 보세요.'}</p>
          )}
          {loading && <p className="list-state">불러오는 중…</p>}
          {hasMore && <div ref={sentinel} className="sentinel" aria-hidden="true" />}
        </section>
      ) : (
        <section role="tabpanel" aria-label="새 대화">
          <ContactList q={q} />
        </section>
      )}

      {settingsOpen && <ChatSettingsModal isTeacher={me.role === 'TEACHER'} onClose={() => setSettingsOpen(false)} />}
    </main>
  );
}
