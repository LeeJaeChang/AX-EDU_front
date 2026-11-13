import { ApiError, getAccessToken, request } from './client';
import type {
  Role,
  ChatRoom,
  ChatRoomDetail,
  ChatSetting,
  Contact,
  ListContactsQuery,
  ListMessagesQuery,
  ListRoomsQuery,
  Message,
  Page,
  RoomSettingsBody,
  SendMessageBody,
  UploadedFile,
  UserSummary,
} from './types';

interface TokenClaims {
  userId: string;
  role: Role;
  schoolId: string;
  name?: string;
}

/** JWT payload를 읽는다(검증은 서버 몫). 형식이 다르면 null. */
function decodeTokenClaims(token: string | null): TokenClaims | null {
  if (!token) return null;
  const part = token.split('.')[1];
  if (!part) return null;
  try {
    const b64 = part.replace(/-/g, '+').replace(/_/g, '/');
    const bytes = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
    const claims = JSON.parse(new TextDecoder().decode(bytes)) as Partial<TokenClaims>;
    return claims.userId && claims.role ? (claims as TokenClaims) : null;
  } catch {
    return null;
  }
}

/** 채팅 화면이 쓰는 API (명세서 4·5·6장). 실제 서버 구현과 목 구현이 같은 인터페이스를 따른다. */
export interface ChatApi {
  getMe(): Promise<UserSummary>;
  listRooms(q?: ListRoomsQuery): Promise<Page<ChatRoom>>;
  listContacts(q?: ListContactsQuery): Promise<Contact[]>;
  createRoom(counterpartId: string): Promise<ChatRoom>;
  getRoom(roomId: string): Promise<ChatRoomDetail>;
  updateRoomSettings(roomId: string, body: RoomSettingsBody): Promise<ChatRoom>;
  leaveRoom(roomId: string): Promise<void>;
  listMessages(roomId: string, q?: ListMessagesQuery): Promise<Page<Message>>;
  sendMessage(roomId: string, body: SendMessageBody): Promise<Message>;
  uploadFile(file: File): Promise<UploadedFile>;
  markRead(roomId: string, lastReadMessageId: string): Promise<void>;
  getChatSettings(): Promise<ChatSetting>;
  saveChatSettings(body: ChatSetting): Promise<ChatSetting>;
}

export const httpChatApi: ChatApi = {
  // 명세서 2장: 토큰에 userId·role·schoolId가 들어 있다. 별도 API 없이 토큰에서 읽는다.
  async getMe() {
    const claims = decodeTokenClaims(getAccessToken());
    if (!claims) throw new ApiError(401, 'UNAUTHORIZED', '로그인이 필요합니다.');
    return { id: claims.userId, name: claims.name ?? '', role: claims.role };
  },

  async listRooms(q = {}) {
    const { data, meta } = await request<ChatRoom[]>('/chat/rooms', { query: { ...q } });
    return { items: data, nextCursor: meta.nextCursor ?? null, hasMore: !!meta.nextCursor };
  },

  async listContacts(q = {}) {
    return (await request<Contact[]>('/chat/contacts', { query: { ...q } })).data;
  },

  async createRoom(counterpartId) {
    return (await request<ChatRoom>('/chat/rooms', { method: 'POST', body: { counterpartId } })).data;
  },

  async getRoom(roomId) {
    return (await request<ChatRoomDetail>(`/chat/rooms/${roomId}`)).data;
  },

  async updateRoomSettings(roomId, body) {
    return (await request<ChatRoom>(`/chat/rooms/${roomId}/settings`, { method: 'PATCH', body })).data;
  },

  async leaveRoom(roomId) {
    await request<void>(`/chat/rooms/${roomId}/members/me`, { method: 'DELETE' });
  },

  async listMessages(roomId, q = {}) {
    const { data, meta } = await request<Message[]>(`/chat/rooms/${roomId}/messages`, {
      query: { ...q },
    });
    return { items: data, nextCursor: meta.nextCursor ?? null, hasMore: !!meta.hasMore };
  },

  async sendMessage(roomId, body) {
    return (
      await request<Message>(`/chat/rooms/${roomId}/messages`, {
        method: 'POST',
        body,
        headers: { 'Idempotency-Key': body.clientMessageId },
      })
    ).data;
  },

  async uploadFile(file) {
    const form = new FormData();
    form.append('file', file);
    return (await request<UploadedFile>('/files', { method: 'POST', body: form })).data;
  },

  async markRead(roomId, lastReadMessageId) {
    await request<void>(`/chat/rooms/${roomId}/read`, { method: 'POST', body: { lastReadMessageId } });
  },

  async getChatSettings() {
    return (await request<ChatSetting>('/me/chat-settings')).data;
  },

  async saveChatSettings(body) {
    return (await request<ChatSetting>('/me/chat-settings', { method: 'PUT', body })).data;
  },
};
