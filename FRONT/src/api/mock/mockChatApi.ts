import { toMs } from '../../lib/format';
import { ApiError } from '../client';
import type { ChatApi } from '../chatApi';
import type { ChatRoomDetail, Contact, Message, UploadedFile } from '../types';
import {
  addRoom,
  canSend,
  clone,
  delay,
  effectiveHours,
  emit,
  findDirect,
  genId,
  me,
  messages,
  nowIso,
  placeholderOf,
  postMessage,
  rooms,
  settingOf,
  systemMessage,
  toRoomView,
  uploads,
  users,
  activeComplaintIdFor,
  type RoomRow,
} from './store';
import { seed } from './seed';

seed();

function getRow(roomId: string): RoomRow {
  const row = rooms.get(roomId);
  if (!row || !row.members.includes(me().id)) throw new ApiError(404, 'NOT_FOUND', '채팅방을 찾을 수 없습니다.');
  return row;
}

/** 역할별로 보이는 대화 상대 (명세서 4.2) */
function contactsOf(): string[] {
  const viewer = me();
  if (viewer.role === 'TEACHER') return ['usr_s1', 'usr_s5', 'usr_s6', 'usr_s4', 'usr_s3', 'usr_s2', 'usr_p1', 'usr_p2'];
  if (viewer.role === 'PARENT') return ['usr_t1', 'usr_t2'];
  return ['usr_t1'];
}


/** 데모: 교사가 학생에게 보내면 학생이 잠시 뒤 답한다(실시간 수신 확인용) */
function scheduleStudentReply(row: RoomRow) {
  const student = row.members.map((id) => users[id]).find((x) => x.role === 'STUDENT');
  if (row.type !== 'DIRECT' || !student) return;
  window.setTimeout(() => {
    const list = messages.get(row.id)!;
    const lastOther = [...list].reverse().find((m) => m.sender && m.sender.id !== student.id);
    if (lastOther) {
      list.forEach((m) => m.sender && m.sender.id !== student.id && (m.readByCounterpart = true));
      emit({ event: 'message.read', data: { roomId: row.id, readerId: student.id, lastReadMessageId: lastOther.id } });
    }
    postMessage({ id: genId('msg_'), roomId: row.id, type: 'TEXT', sender: student, content: '네 선생님, 확인했습니다!', attachments: [], createdAt: nowIso() });
  }, 2500);
}

export const mockChatApi: ChatApi = {
  async getMe() {
    await delay(30);
    return clone(me());
  },

  async listRooms({ q, type, cursor, limit = 20 } = {}) {
    await delay();
    const viewer = me();
    const keyword = q?.trim().toLowerCase();
    const all = [...rooms.values()]
      .filter((r) => r.members.includes(viewer.id))
      .filter((r) => !type || r.type === type)
      .map((r) => toRoomView(r, viewer))
      .filter((r) => !keyword || [r.title, r.counterpart?.name, r.counterpart?.studentNumber].join(' ').toLowerCase().includes(keyword))
      .sort((a, b) => toMs(b.lastMessage?.sentAt) - toMs(a.lastMessage?.sentAt));
    const start = cursor ? Number(cursor) : 0;
    const next = start + limit < all.length ? String(start + limit) : null;
    return { items: all.slice(start, start + limit), nextCursor: next, hasMore: next !== null };
  },

  async listContacts({ q, role } = {}) {
    await delay();
    const viewer = me();
    const keyword = q?.trim();
    return contactsOf()
      .map((id) => users[id])
      .filter((c) => !role || c.role === role)
      .filter((c) => !keyword || c.name.includes(keyword) || c.studentNumber?.includes(keyword))
      .map<Contact>((c) => ({ ...clone(c), existingRoomId: findDirect(viewer.id, c.id)?.id ?? null }));
  },

  async createRoom(counterpartId) {
    await delay();
    const viewer = me();
    const existing = findDirect(viewer.id, counterpartId);
    if (existing) throw new ApiError(409, 'ROOM_ALREADY_EXISTS', '이미 대화 중인 상대입니다.', { roomId: existing.id });
    if (!contactsOf().includes(counterpartId)) throw new ApiError(403, 'FORBIDDEN', '대화할 수 없는 상대입니다.');
    const id = genId('room_');
    addRoom({ id, type: 'DIRECT', members: [viewer.id, counterpartId], createdAt: nowIso(), availableHoursOverride: null });
    const row = rooms.get(id)!;
    emit({ event: 'room.created', data: toRoomView(row, users[counterpartId]) });
    return toRoomView(row, viewer);
  },

  async getRoom(roomId) {
    await delay();
    const viewer = me();
    const row = getRow(roomId);
    const cp = toRoomView(row, viewer).counterpart;
    const active = activeComplaintIdFor(roomId, viewer);
    const detail: ChatRoomDetail = {
      ...toRoomView(row, viewer),
      availableHours: effectiveHours(row),
      inputPlaceholder: placeholderOf(row, viewer),
      canSend: canSend(row, viewer),
      canFileComplaint: viewer.role === 'PARENT' && row.type === 'DIRECT' && cp?.role === 'TEACHER',
      canRequestHelp: viewer.role === 'TEACHER' && !!active,
    };
    return detail;
  },

  async updateRoomSettings(roomId, body) {
    await delay();
    const viewer = me();
    const row = getRow(roomId);
    if (body.muted !== undefined) {
      if (body.muted) row.muted.add(viewer.id);
      else row.muted.delete(viewer.id);
    }
    if (body.availableHoursOverride !== undefined) {
      if (viewer.role !== 'TEACHER') throw new ApiError(403, 'FORBIDDEN', '대화 가능 시간은 교사만 바꿀 수 있습니다.');
      const h = body.availableHoursOverride;
      if (h && h.from >= h.to) {
        throw new ApiError(400, 'VALIDATION_ERROR', '시작 시간이 종료 시간보다 빨라야 합니다.', { fields: { availableHoursOverride: 'from must be before to' } });
      }
      row.availableHoursOverride = h;
      emit({ event: 'room.updated', data: { roomId, availableHours: h, inputPlaceholder: placeholderOf(row, viewer), canSend: canSend(row, viewer) } });
    }
    return toRoomView(row, viewer);
  },

  async leaveRoom(roomId) {
    await delay();
    const viewer = me();
    const row = getRow(roomId);
    if (activeComplaintIdFor(roomId, viewer)) {
      throw new ApiError(409, 'INVALID_COMPLAINT_STATUS', '민원이 진행 중인 방은 나갈 수 없습니다.');
    }
    row.members = row.members.filter((id) => id !== viewer.id);
    postMessage(systemMessage(roomId, `${viewer.name}님이 나갔습니다.`));
  },

  async listMessages(roomId, { before, after, limit = 30 } = {}) {
    await delay();
    getRow(roomId);
    const all = messages.get(roomId) ?? [];
    let slice: Message[];
    if (after) {
      slice = all.slice(all.findIndex((m) => m.id === after) + 1);
    } else {
      const end = before ? all.findIndex((m) => m.id === before) : all.length;
      slice = all.slice(Math.max(0, end - limit), end);
    }
    const oldest = slice[0];
    const hasMore = !after && !!oldest && all.indexOf(oldest) > 0;
    return { items: clone(slice).reverse(), nextCursor: hasMore ? oldest.id : null, hasMore };
  },

  async sendMessage(roomId, body) {
    await delay(250);
    const viewer = me();
    const row = getRow(roomId);
    if (row.type === 'NOTICE') throw new ApiError(403, 'NOTICE_ROOM_READ_ONLY', '공지형 채팅방에는 메시지를 보낼 수 없습니다.');
    if (!body.content.trim() && body.attachmentIds.length === 0) {
      throw new ApiError(400, 'VALIDATION_ERROR', '메시지를 입력해 주세요.', { fields: { content: 'required' } });
    }
    if (body.content.length > 2000) {
      throw new ApiError(400, 'VALIDATION_ERROR', '메시지는 2,000자까지 보낼 수 있습니다.', { fields: { content: 'max 2000' } });
    }
    if (!canSend(row, viewer)) {
      const h = effectiveHours(row)!;
      throw new ApiError(403, 'CHAT_OUTSIDE_AVAILABLE_HOURS', `대화 가능 시간은 ${h.from}~${h.to} 입니다.`, { availableFrom: h.from, availableTo: h.to });
    }
    const list = messages.get(roomId)!;
    const dup = list.find((m) => m.sender?.id === viewer.id && m.clientMessageId === body.clientMessageId);
    if (dup) return clone(dup);

    const attachments = body.attachmentIds
      .map((id) => uploads.get(id))
      .filter((f): f is UploadedFile => !!f)
      .map((f) => ({ id: f.id, name: f.name, mimeType: f.mimeType, size: f.size, url: f.url, thumbnailUrl: f.mimeType.startsWith('image/') ? f.url : null }));

    const msg: Message = {
      id: genId('msg_'),
      roomId,
      type: attachments.length && !body.content ? (attachments[0].mimeType.startsWith('image/') ? 'IMAGE' : 'FILE') : 'TEXT',
      sender: clone(viewer),
      content: body.content,
      attachments,
      clientMessageId: body.clientMessageId,
      readByCounterpart: false,
      createdAt: nowIso(),
    };
    postMessage(msg);
    if (viewer.role === 'TEACHER') scheduleStudentReply(row);
    return clone(msg);
  },

  async uploadFile(file) {
    await delay(400);
    const isImage = file.type.startsWith('image/');
    if (file.size > (isImage ? 10 : 20) * 1024 * 1024) {
      throw new ApiError(413, 'FILE_TOO_LARGE', `${isImage ? '이미지는 10MB' : '문서는 20MB'}까지 올릴 수 있습니다.`);
    }
    const uploaded: UploadedFile = {
      id: genId('file_'),
      name: file.name,
      mimeType: file.type || 'application/octet-stream',
      size: file.size,
      url: URL.createObjectURL(file),
      expiresAt: new Date(Date.now() + 3600_000).toISOString(),
    };
    uploads.set(uploaded.id, uploaded);
    return clone(uploaded);
  },

  async markRead(roomId, lastReadMessageId) {
    await delay(50);
    const viewer = me();
    const row = getRow(roomId);
    row.unread[viewer.id] = 0;
    for (const m of messages.get(roomId) ?? []) if (m.sender && m.sender.id !== viewer.id) m.readByCounterpart = true;
    emit({ event: 'room.unread_changed', data: { roomId, unreadCount: 0 } });
    emit({ event: 'message.read', data: { roomId, readerId: viewer.id, lastReadMessageId } });
  },

  async getChatSettings() {
    await delay();
    return clone(settingOf(me().id));
  },

  async saveChatSettings(body) {
    await delay();
    const viewer = me();
    if (viewer.role !== 'TEACHER') {
      // 학부모·학생은 알림만 바꿀 수 있다
      const s = settingOf(viewer.id);
      s.pushEnabled = body.pushEnabled;
      return clone(s);
    }
    if (body.availableHours.from >= body.availableHours.to) {
      throw new ApiError(400, 'VALIDATION_ERROR', '시작 시간이 종료 시간보다 빨라야 합니다.', { fields: { availableHours: 'from must be before to' } });
    }
    Object.assign(settingOf(viewer.id), clone(body));
    for (const row of rooms.values()) {
      if (!row.members.includes(viewer.id) || row.type === 'NOTICE') continue;
      emit({ event: 'room.updated', data: { roomId: row.id, availableHours: row.availableHoursOverride, inputPlaceholder: placeholderOf(row, viewer), canSend: canSend(row, viewer) } });
    }
    return clone(settingOf(viewer.id));
  },
};
