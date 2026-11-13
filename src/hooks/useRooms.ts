import { toMs } from '../lib/format';
import { useCallback, useEffect, useRef, useState } from 'react';
import { chatApi, realtime, type ChatRoom, type SocketEvent } from '../api';

const sortByRecent = (list: ChatRoom[]) =>
  [...list].sort((a, b) => toMs(b.lastMessage?.sentAt) - toMs(a.lastMessage?.sentAt));

/** GET /chat/rooms + 실시간 갱신 (message.created · room.created · room.updated · room.unread_changed) */
export function useRooms(q: string, myId: string) {
  const [rooms, setRooms] = useState<ChatRoom[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const reqId = useRef(0);

  const load = useCallback(
    async (next?: string) => {
      const id = ++reqId.current;
      setLoading(true);
      setError(null);
      try {
        const page = await chatApi.listRooms({ q: q || undefined, cursor: next, limit: 20 });
        if (id !== reqId.current) return; // 오래된 검색 응답은 버린다
        setRooms((prev) => (next ? [...prev, ...page.items] : page.items));
        setCursor(page.nextCursor);
      } catch (e) {
        if (id === reqId.current) setError((e as Error).message);
      } finally {
        if (id === reqId.current) setLoading(false);
      }
    },
    [q],
  );

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    return realtime.subscribe((e: SocketEvent) => {
      switch (e.event) {
        case 'message.created': {
          const m = e.data;
          setRooms((prev) => {
            const target = prev.find((r) => r.id === m.roomId);
            if (!target) return prev;
            const updated: ChatRoom = {
              ...target,
              lastMessage: { id: m.id, type: m.type, preview: m.content, sentAt: m.createdAt },
              unreadCount: m.sender?.id === myId ? target.unreadCount : (target.unreadCount ?? 0) + 1,
            };
            return sortByRecent(prev.map((r) => (r.id === m.roomId ? updated : r)));
          });
          break;
        }
        case 'room.created':
          setRooms((prev) => (prev.some((r) => r.id === e.data.id) ? prev : [e.data, ...prev]));
          break;
        case 'room.updated':
          setRooms((prev) =>
            prev.map((r) => (r.id === e.data.roomId ? { ...r, availableHours: e.data.availableHours } : r)),
          );
          break;
        case 'complaint.status_changed':
          void load();
          break;
        case 'room.unread_changed':
          setRooms((prev) =>
            prev.map((r) => (r.id === e.data.roomId ? { ...r, unreadCount: e.data.unreadCount } : r)),
          );
          break;
      }
    });
  }, [myId, load]);

  const loadMore = useCallback(() => {
    if (cursor && !loading) void load(cursor);
  }, [cursor, loading, load]);

  return { rooms, loading, error, hasMore: !!cursor, loadMore, reload: () => load() };
}
