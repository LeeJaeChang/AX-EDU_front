import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiError, chatApi, realtime, type ChatRoomDetail } from '../api';
import Avatar from '../components/Avatar';
import Composer from '../components/Composer';
import ComplaintBanner from '../components/complaint/ComplaintBanner';
import TeacherComplaintDrawer from '../components/complaint/TeacherComplaintDrawer';
import ConfirmModal from '../components/ConfirmModal';
import { ChevronLeftIcon } from '../components/Icons';
import MessageList from '../components/MessageList';
import RoomSettingsModal from '../components/RoomSettingsModal';
import { useMe } from '../hooks/useMe';
import { useMessages } from '../hooks/useMessages';

const NEAR_BOTTOM = 120;

export default function ChatRoomPage() {
  const { roomId = '' } = useParams();
  const navigate = useNavigate();
  const me = useMe();

  const [room, setRoom] = useState<ChatRoomDetail | null>(null);
  const [roomError, setRoomError] = useState<string | null>(null);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [leaveOpen, setLeaveOpen] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const [leaveError, setLeaveError] = useState<string | null>(null);
  const [drawerId, setDrawerId] = useState<string | null>(null);

  const { messages, hasMore, loading, loadingOlder, error, loadOlder, send, retry, discard } = useMessages(roomId, me);

  // ---- 방 상세 (헤더 + 입력창 안내 문구) ----
  const loadRoom = useCallback(() => {
    return chatApi
      .getRoom(roomId)
      .then(setRoom)
      .catch((e: Error) => setRoomError(e.message));
  }, [roomId]);

  useEffect(() => {
    setRoom(null);
    setRoomError(null);
    void loadRoom();
  }, [loadRoom]);

  useEffect(
    () =>
      realtime.subscribe((e) => {
        // 민원 상태가 바뀌면 activeComplaintId·canRequestHelp가 달라진다
        if (e.event === 'complaint.status_changed') void loadRoom();
        if (e.event === 'room.updated' && e.data.roomId === roomId) {
          setRoom((r) =>
            r
              ? { ...r, inputPlaceholder: e.data.inputPlaceholder, canSend: e.data.canSend, availableHours: e.data.availableHours ?? r.availableHours }
              : r,
          );
        }
      }),
    [roomId, loadRoom],
  );

  // ---- 스크롤 ----
  const scroller = useRef<HTMLDivElement>(null);
  const content = useRef<HTMLDivElement>(null);
  const prevHeight = useRef<number | null>(null);
  const stickToBottom = useRef(true);
  const lastId = messages[messages.length - 1]?.clientMessageId ?? messages[messages.length - 1]?.id;

  const onScroll = useCallback(() => {
    const el = scroller.current;
    if (!el) return;
    stickToBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < NEAR_BOTTOM;
    if (el.scrollTop < 80 && hasMore && !loadingOlder) {
      prevHeight.current = el.scrollHeight;
      void loadOlder();
    }
  }, [hasMore, loadingOlder, loadOlder]);

  useLayoutEffect(() => {
    const el = scroller.current;
    if (!el) return;
    if (prevHeight.current !== null) {
      if (loadingOlder) return; // 이전 내역 로딩 중에는 위치를 건드리지 않는다
      // 이전 내역을 위에 붙인 뒤 보던 위치 유지
      el.scrollTop += el.scrollHeight - prevHeight.current;
      prevHeight.current = null;
      return;
    }
    const mineLast = messages[messages.length - 1]?.sender?.id === me.id;
    if (stickToBottom.current || mineLast) el.scrollTop = el.scrollHeight;
  }, [lastId, messages, me.id, loadingOlder]);

  // 카드처럼 늦게 채워지는 말풍선 때문에 높이가 바뀌어도 바닥에 붙어 있게
  useEffect(() => {
    const el = content.current;
    if (!el) return;
    const ro = new ResizeObserver(() => {
      const sc = scroller.current;
      if (sc && stickToBottom.current && prevHeight.current === null) sc.scrollTop = sc.scrollHeight;
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  async function leave() {
    setLeaving(true);
    setLeaveError(null);
    try {
      await chatApi.leaveRoom(roomId);
      navigate('/chat', { replace: true });
    } catch (e) {
      setLeaveError(
        e instanceof ApiError && e.code === 'INVALID_COMPLAINT_STATUS'
          ? '민원이 진행 중인 방은 나갈 수 없습니다. 민원 기록 보존을 위해서입니다.'
          : (e as Error).message,
      );
    } finally {
      setLeaving(false);
    }
  }

  const title = room?.counterpart?.name ?? room?.title ?? '';

  return (
    <div className="room-page">
      <nav className="topbar">
        <Link to="/chat" className="back-link">
          <ChevronLeftIcon width={18} height={18} />
          목록으로
        </Link>
      </nav>

      <div className="room-body">
        <header className="room-head">
          {room ? (
            <>
              <Avatar
                name={title}
                imageUrl={room.counterpart?.profileImageUrl}
                variant={room.type === 'NOTICE' ? 'notice' : 'initial'}
                size={40}
              />
              <h1 className="room-head__name">{title}</h1>
              <div className="room-head__actions">
                {room.canFileComplaint && !room.activeComplaintId && (
                  <button type="button" className="btn btn--sm" onClick={() => navigate(`/chat/${roomId}/complaint`)}>
                    민원하기
                  </button>
                )}
                <button type="button" className="btn btn--soft btn--sm" onClick={() => setSettingsOpen(true)}>설정</button>
                <button type="button" className="btn btn--soft btn--sm" onClick={() => setLeaveOpen(true)}>나가기</button>
              </div>
            </>
          ) : (
            <p className="room-head__loading">{roomError ?? '불러오는 중…'}</p>
          )}
        </header>

        {room?.activeComplaintId && (
          <ComplaintBanner roomId={roomId} complaintId={room.activeComplaintId} viewerRole={me.role} onOpen={setDrawerId} />
        )}

        <div className="messages-scroll" ref={scroller} onScroll={onScroll}>
          {loadingOlder && <p className="list-state">이전 대화를 불러오는 중…</p>}
          {loading && <p className="list-state">대화를 불러오는 중…</p>}
          {error && <p className="inline-error" role="alert">{error}</p>}
          <div ref={content}>
          <MessageList
            messages={messages}
            myId={me.id}
            viewerRole={me.role}
            onRetry={retry}
            onDiscard={discard}
            onOpenComplaint={me.role === 'TEACHER' ? setDrawerId : undefined}
          />
          </div>
        </div>

        <Composer
          placeholder={room?.inputPlaceholder ?? ''}
          disabled={!room || !room.canSend}
          onSend={send}
        />
      </div>

      {settingsOpen && room && (
        <RoomSettingsModal
          room={room}
          isTeacher={me.role === 'TEACHER'}
          onClose={() => setSettingsOpen(false)}
          onSaved={(patch) => setRoom((r) => (r ? { ...r, ...patch } : r))}
        />
      )}
      {drawerId && <TeacherComplaintDrawer complaintId={drawerId} onClose={() => setDrawerId(null)} />}
      {leaveOpen && (
        <ConfirmModal
          title="채팅방 나가기"
          message="내 목록에서 이 대화가 사라지고, 상대에게는 나갔다는 안내가 표시됩니다."
          confirmLabel="나가기"
          busy={leaving}
          error={leaveError}
          onConfirm={leave}
          onClose={() => {
            setLeaveOpen(false);
            setLeaveError(null);
          }}
        />
      )}
    </div>
  );
}
