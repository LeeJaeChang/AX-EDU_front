import { API_HOST_WS, getAccessToken } from '../api/client';
import type { SocketEvent } from '../api/types';

// 명세서 8장: 전송은 REST, 수신·갱신은 WebSocket 하나로 받는다.

export type SocketListener = (e: SocketEvent) => void;
export type ReconnectListener = () => void;

export interface RealtimeClient {
  connect(): void;
  disconnect(): void;
  subscribe(listener: SocketListener): () => void;
  /** 재접속 직후 호출 — 화면은 `?after=`로 놓친 메시지를 채운다 */
  onReconnect(listener: ReconnectListener): () => void;
}

/** 리스너 관리만 하는 이벤트 버스. 목 구현이 그대로 쓴다. */
export class EventBus implements RealtimeClient {
  protected listeners = new Set<SocketListener>();
  protected reconnectListeners = new Set<ReconnectListener>();

  connect() {}
  disconnect() {}

  subscribe(listener: SocketListener) {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  onReconnect(listener: ReconnectListener) {
    this.reconnectListeners.add(listener);
    return () => this.reconnectListeners.delete(listener);
  }

  emit(e: SocketEvent) {
    this.listeners.forEach((l) => l(e));
  }
}

const PING_INTERVAL = 30_000;
const MAX_BACKOFF = 30_000;

export class WebSocketClient extends EventBus {
  private ws: WebSocket | null = null;
  private pingTimer: number | undefined;
  private retryTimer: number | undefined;
  private retries = 0;
  private closedByUser = false;
  private hasConnectedOnce = false;

  connect() {
    this.closedByUser = false;
    if (this.ws && this.ws.readyState <= WebSocket.OPEN) return;
    const token = getAccessToken();
    if (!token) return;

    const ws = new WebSocket(`${API_HOST_WS}/ws/axedu/v1?token=${encodeURIComponent(token)}`);
    this.ws = ws;

    ws.onopen = () => {
      this.retries = 0;
      this.pingTimer = window.setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify({ event: 'ping' }));
      }, PING_INTERVAL);
      if (this.hasConnectedOnce) this.reconnectListeners.forEach((l) => l());
      this.hasConnectedOnce = true;
    };

    ws.onmessage = (msg) => {
      try {
        const frame = JSON.parse(String(msg.data)) as SocketEvent;
        if (frame.event !== 'pong') this.emit(frame);
      } catch {
        /* 알 수 없는 프레임은 무시 */
      }
    };

    ws.onclose = () => {
      window.clearInterval(this.pingTimer);
      this.ws = null;
      if (this.closedByUser) return;
      const delay = Math.min(1000 * 2 ** this.retries, MAX_BACKOFF);
      this.retries += 1;
      this.retryTimer = window.setTimeout(() => this.connect(), delay);
    };
  }

  disconnect() {
    this.closedByUser = true;
    window.clearTimeout(this.retryTimer);
    window.clearInterval(this.pingTimer);
    if (this.ws) {
      // 닫히는 중인 소켓이 새 연결 상태를 건드리거나 이전 계정 이벤트를 흘리지 않게 핸들러를 뗀다
      this.ws.onopen = this.ws.onmessage = this.ws.onclose = null;
      this.ws.close();
    }
    this.ws = null;
    this.retries = 0;
    this.hasConnectedOnce = false;
  }
}
