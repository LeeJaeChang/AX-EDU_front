import { useCallback, useEffect, useRef, useState } from 'react';
import { chatApi, realtime, type Attachment, type Message, type UserSummary } from '../api';
import { newClientId } from '../api/client';

export type SendStatus = 'sending' | 'failed';

/** 화면에 그리는 메시지. 낙관적 전송 중이거나 실패한 메시지는 status를 가진다. */
export interface ViewMessage extends Message {
  status?: SendStatus;
  errorMessage?: string;
}

interface Outgoing {
  content: string;
  attachments: Attachment[];
}

const PAGE = 30;

/**
 * 채팅 상세 메시지 상태 (명세서 5장 + 8장)
 * - 첫 로드: 최신 30개 (서버는 최신순 → 오래된 순으로 뒤집어 보관)
 * - 위로 스크롤: ?before=가장 오래된 ID
 * - 실시간: message.created / message.read, 재접속 시 ?after=마지막 ID
 * - 전송: clientMessageId로 낙관적 추가, 실패 시 같은 키로 재시도
 */
export function useMessages(roomId: string, me: UserSummary) {
  const [messages, setMessages] = useState<ViewMessage[]>([]);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const latest = useRef<ViewMessage[]>([]);
  latest.current = messages;

  const lastServerId = () => [...latest.current].reverse().find((m) => !m.status)?.id;

  const markRead = useCallback(
    (list: ViewMessage[]) => {
      const last = [...list].reverse().find((m) => !m.status);
      if (last) chatApi.markRead(roomId, last.id).catch(() => {});
    },
    [roomId],
  );

  // 첫 로드
  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError(null);
    setMessages([]);
    chatApi
      .listMessages(roomId, { limit: PAGE })
      .then((page) => {
        if (!alive) return;
        const asc = [...page.items].reverse();
        setMessages(asc);
        setHasMore(page.hasMore);
        markRead(asc);
      })
      .catch((e: Error) => alive && setError(e.message))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [roomId, markRead]);

  const loadOlder = useCallback(async () => {
    const oldest = latest.current.find((m) => !m.status);
    if (!oldest || loadingOlder || !hasMore) return;
    setLoadingOlder(true);
    try {
      const page = await chatApi.listMessages(roomId, { before: oldest.id, limit: PAGE });
      setMessages((prev) => [...[...page.items].reverse(), ...prev]);
      setHasMore(page.hasMore);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoadingOlder(false);
    }
  }, [roomId, loadingOlder, hasMore]);

  // 실시간 수신
  useEffect(() => {
    const unsub = realtime.subscribe((e) => {
      if (e.event === 'message.created' && e.data.roomId === roomId) {
        const incoming = e.data;
        setMessages((prev) => {
          if (prev.some((m) => m.id === incoming.id)) return prev;
          // 내가 보낸 메시지의 에코면 낙관적 메시지를 교체
          const i = incoming.clientMessageId
            ? prev.findIndex((m) => m.clientMessageId === incoming.clientMessageId)
            : -1;
          if (i >= 0) return prev.map((m, idx) => (idx === i ? incoming : m));
          return [...prev, incoming];
        });
        if (incoming.sender?.id !== me.id) chatApi.markRead(roomId, incoming.id).catch(() => {});
      }
      if (e.event === 'message.read' && e.data.roomId === roomId && e.data.readerId !== me.id) {
        const upto = e.data.lastReadMessageId;
        setMessages((prev) => {
          const idx = prev.findIndex((m) => m.id === upto);
          if (idx < 0) return prev;
          return prev.map((m, i) => (i <= idx && m.sender?.id === me.id ? { ...m, readByCounterpart: true } : m));
        });
      }
    });

    // 재접속 후 놓친 메시지 채우기
    const unsubRe = realtime.onReconnect(async () => {
      const after = lastServerId();
      if (!after) return;
      try {
        const page = await chatApi.listMessages(roomId, { after, limit: 100 });
        const missed = [...page.items].reverse();
        setMessages((prev) => [...prev, ...missed.filter((m) => !prev.some((p) => p.id === m.id))]);
      } catch {
        /* 다음 재접속 때 다시 시도 */
      }
    });

    return () => {
      unsub();
      unsubRe();
    };
  }, [roomId, me.id]);

  const deliver = useCallback(
    async (clientMessageId: string, out: Outgoing) => {
      try {
        const saved = await chatApi.sendMessage(roomId, {
          clientMessageId,
          type: 'TEXT',
          content: out.content,
          attachmentIds: out.attachments.map((a) => a.id),
        });
        setMessages((prev) => {
          // 낙관적 메시지를 서버 응답으로 바꾸고, 실시간 에코가 먼저 와서 이미 바뀌었으면 한 건만 남긴다.
          // (에코가 낙관적 메시지를 이미 대체한 경우 id로 걸러 내면 메시지 자체가 사라졌다)
          const replaced = prev.map((m) => (m.clientMessageId === clientMessageId ? saved : m));
          return replaced.filter((m, i) => m.id !== saved.id || replaced.findIndex((x) => x.id === saved.id) === i);
        });
      } catch (e) {
        setMessages((prev) =>
          prev.map((m) =>
            m.clientMessageId === clientMessageId
              ? { ...m, status: 'failed', errorMessage: (e as Error).message }
              : m,
          ),
        );
        throw e;
      }
    },
    [roomId],
  );

  const send = useCallback(
    (out: Outgoing) => {
      const clientMessageId = newClientId();
      const optimistic: ViewMessage = {
        id: `tmp_${clientMessageId}`,
        roomId,
        type: 'TEXT',
        sender: me,
        content: out.content,
        attachments: out.attachments,
        clientMessageId,
        readByCounterpart: false,
        createdAt: new Date().toISOString(),
        status: 'sending',
      };
      setMessages((prev) => [...prev, optimistic]);
      return deliver(clientMessageId, out);
    },
    [roomId, me, deliver],
  );

  const retry = useCallback(
    (m: ViewMessage) => {
      if (!m.clientMessageId) return;
      setMessages((prev) =>
        prev.map((x) => (x.clientMessageId === m.clientMessageId ? { ...x, status: 'sending', errorMessage: undefined } : x)),
      );
      deliver(m.clientMessageId, { content: m.content, attachments: m.attachments ?? [] }).catch(() => {});
    },
    [deliver],
  );

  const discard = useCallback((m: ViewMessage) => {
    setMessages((prev) => prev.filter((x) => x.clientMessageId !== m.clientMessageId));
  }, []);

  return { messages, hasMore, loading, loadingOlder, error, loadOlder, send, retry, discard };
}
