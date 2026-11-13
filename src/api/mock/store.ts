// 목 서버의 메모리 DB. 여러 사용자(교사·학부모)를 오가며 같은 데이터를 본다.
import { toMs } from '../../lib/format';
import { EventBus } from '../../realtime/socket';
import type {
  AiExplanation,
  ChatRoom,
  ChatSetting,
  ComplaintStatus,
  ContactMethod,
  Escalation,
  Message,
  SocketEvent,
  SummaryCard,
  CardSuggestion,
  TimeRange,
  UserSummary,
  UploadedFile,
} from '../types';

export const delay = (ms = 180) => new Promise((r) => setTimeout(r, ms));
export const nowIso = () => new Date().toISOString();
export const genId = (prefix: string) =>
  `${prefix}${Date.now().toString(36).toUpperCase()}${Math.random().toString(36).slice(2, 8).toUpperCase()}`;
export const clone = <T>(v: T): T => structuredClone(v);

export const bus = new EventBus();
export const emit = (e: Omit<SocketEvent, 'sentAt'>) => bus.emit({ ...e, sentAt: nowIso() } as SocketEvent);

// ---------------- 사용자 ----------------

const u = (id: string, name: string, role: UserSummary['role'], extra: Partial<UserSummary> = {}): UserSummary => ({
  id,
  name,
  role,
  profileImageUrl: null,
  ...extra,
});

export const users: Record<string, UserSummary> = {
  usr_t1: u('usr_t1', '김수학', 'TEACHER'),
  usr_t2: u('usr_t2', '이영어', 'TEACHER'),
  usr_s1: u('usr_s1', '김리현', 'STUDENT', { studentNumber: '30901' }),
  usr_s2: u('usr_s2', '홍세아', 'STUDENT', { studentNumber: '10110' }),
  usr_s3: u('usr_s3', '이우진', 'STUDENT', { studentNumber: '10104' }),
  usr_s4: u('usr_s4', '최은서', 'STUDENT', { studentNumber: '20202' }),
  usr_s5: u('usr_s5', '김민수', 'STUDENT', { studentNumber: '30902' }),
  usr_s6: u('usr_s6', '박지후', 'STUDENT', { studentNumber: '30903' }),
  usr_p1: u('usr_p1', '김리현 학부모', 'PARENT', { childStudentIds: ['usr_s1'] }),
  usr_p2: u('usr_p2', '김민수 학부모', 'PARENT', { childStudentIds: ['usr_s5'] }),
  usr_notice: u('usr_notice', '슈퍼스쿨', 'NOTICE'),
};

/** 데모 계정 전환 (목 모드 전용) */
export const DEMO_ACCOUNTS = [
  { id: 'usr_t1', label: '교사 · 김수학 (3학년 9반 담임)' },
  { id: 'usr_p1', label: '학부모 · 김리현 보호자' },
  { id: 'usr_p2', label: '학부모 · 김민수 보호자' },
];

let currentId = 'usr_t1';
try {
  const saved = sessionStorage.getItem('axedu.mockUser');
  if (saved && users[saved]) currentId = saved;
} catch {
  /* 저장소 접근 불가 */
}

export const me = () => users[currentId];
export function setCurrentUser(id: string) {
  if (!users[id]) return;
  currentId = id;
  try {
    sessionStorage.setItem('axedu.mockUser', id);
  } catch {
    /* 무시 */
  }
}

/** 학번 30901 → 3학년 9반 */
export function gradeClassOf(student: UserSummary) {
  const n = student.studentNumber ?? '00000';
  return { grade: Number(n.slice(0, 1)), classNo: Number(n.slice(1, 3)) };
}

// ---------------- 채팅 설정 ----------------

const defaultSetting = (): ChatSetting => ({
  availableHours: { from: '09:00', to: '18:00' },
  availableDays: ['MON', 'TUE', 'WED', 'THU', 'FRI'],
  allowOutsideHoursFor: ['TEACHER'],
  outsideHoursNotice: '대화 가능 시간은 {from}~{to} 입니다.',
  pushEnabled: true,
});
const settings = new Map<string, ChatSetting>();
export function settingOf(userId: string) {
  if (!settings.has(userId)) settings.set(userId, defaultSetting());
  return settings.get(userId)!;
}

// ---------------- 채팅방 ----------------

export interface RoomRow {
  id: string;
  type: ChatRoom['type'];
  groupTitle?: string;
  members: string[];
  createdAt: string;
  availableHoursOverride: TimeRange | null;
  muted: Set<string>;
  unread: Record<string, number>;
}

export const rooms = new Map<string, RoomRow>();
export const messages = new Map<string, Message[]>();
export const uploads = new Map<string, UploadedFile>();

export function systemMessage(roomId: string, content: string, createdAt = nowIso(), id = genId('msg_')): Message {
  return { id, roomId, type: 'SYSTEM', sender: null, content, attachments: [], createdAt };
}

export function addRoom(row: Omit<RoomRow, 'muted' | 'unread'> & { unread?: Record<string, number> }, seed: Message[] = []) {
  rooms.set(row.id, { ...row, muted: new Set(), unread: row.unread ?? {} });
  messages.set(row.id, [systemMessage(row.id, '채팅방이 생성되었습니다.', row.createdAt, `msg_${row.id}_0`), ...seed]);
}

export function getRoomRow(roomId: string) {
  return rooms.get(roomId);
}

export const teacherOf = (row: RoomRow) => row.members.map((id) => users[id]).find((m) => m.role === 'TEACHER') ?? null;

export function effectiveHours(row: RoomRow): TimeRange | null {
  if (row.type === 'NOTICE') return null;
  if (row.availableHoursOverride) return row.availableHoursOverride;
  const t = teacherOf(row);
  return t ? settingOf(t.id).availableHours : null;
}

const DAY_KEYS = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'] as const;

/** KST 기준 대화 가능 여부 (방 재정의 → 상대 교사 설정 순) */
export function isWithinHours(row: RoomRow, at = new Date()) {
  const range = effectiveHours(row);
  if (!range) return true;
  const kst = new Date(at.getTime() + 9 * 3600_000);
  const hm = `${String(kst.getUTCHours()).padStart(2, '0')}:${String(kst.getUTCMinutes()).padStart(2, '0')}`;
  const t = teacherOf(row);
  const days = t ? settingOf(t.id).availableDays : null;
  const dayOk = !days || days.includes(DAY_KEYS[kst.getUTCDay()]);
  return dayOk && hm >= range.from && hm < range.to;
}

export function canSend(row: RoomRow, viewer: UserSummary) {
  if (row.type === 'NOTICE') return false;
  if (viewer.role === 'TEACHER') return true; // 교사 본인은 시간 제한 없음
  const t = teacherOf(row);
  if (t && settingOf(t.id).allowOutsideHoursFor.includes(viewer.role)) return true;
  return isWithinHours(row);
}

export function placeholderOf(row: RoomRow, viewer: UserSummary) {
  if (row.type === 'NOTICE') return '공지형 채팅방은 답장할 수 없습니다.';
  const h = effectiveHours(row);
  const t = teacherOf(row);
  if (!h || !t) return '메시지를 입력하세요.';
  const notice = settingOf(t.id).outsideHoursNotice.replace('{from}', h.from).replace('{to}', h.to);
  if (viewer.role !== 'TEACHER' && !canSend(row, viewer)) return `${notice} 지금은 보낼 수 없어요.`;
  return notice;
}

const ROLE_LABEL: Record<string, string> = { STUDENT: '학생', PARENT: '학부모', TEACHER: '교사' };
export const directTitle = (x: UserSummary) =>
  [x.name, x.studentNumber, ROLE_LABEL[x.role] ? `[${ROLE_LABEL[x.role]}]` : null].filter(Boolean).join(' ');

export function counterpartOf(row: RoomRow, viewer: UserSummary) {
  if (row.type === 'GROUP') return null;
  if (row.type === 'NOTICE') return row.members.map((id) => users[id]).find((m) => m.role === 'NOTICE') ?? null;
  const other = row.members.find((id) => id !== viewer.id);
  return other ? users[other] : null;
}

export function previewOf(m: Message) {
  if (m.type === 'COMPLAINT_CARD') return '[민원 요약 카드]';
  if (m.type === 'COMPLAINT_REPLY') return `[선생님 답변] ${m.content}`;
  if (m.content) return m.content;
  if (m.attachments?.length) return m.type === 'IMAGE' ? '사진' : `파일: ${m.attachments[0].name}`;
  return '';
}

export function toRoomView(row: RoomRow, viewer: UserSummary): ChatRoom {
  const list = messages.get(row.id) ?? [];
  const last = list[list.length - 1];
  const cp = counterpartOf(row, viewer);
  return {
    id: row.id,
    type: row.type,
    title: row.groupTitle ?? (cp ? (row.type === 'NOTICE' ? cp.name : directTitle(cp)) : ''),
    counterpart: cp ? clone(cp) : null,
    availableHours: row.availableHoursOverride,
    isWithinAvailableHours: isWithinHours(row),
    lastMessage: last ? { id: last.id, type: last.type, preview: previewOf(last), sentAt: last.createdAt } : null,
    unreadCount: row.unread[viewer.id] ?? 0,
    muted: row.muted.has(viewer.id),
    activeComplaintId: activeComplaintIdFor(row.id, viewer),
    createdAt: row.createdAt,
  };
}

/** 메시지 저장 + 안 읽은 수 + message.created 이벤트 */
export function postMessage(msg: Message) {
  const row = rooms.get(msg.roomId);
  if (!row) return;
  messages.get(msg.roomId)!.push(msg);
  for (const id of row.members) {
    if (id !== msg.sender?.id) row.unread[id] = (row.unread[id] ?? 0) + 1;
  }
  emit({ event: 'message.created', data: clone(msg) });
}

// ---------------- 민원 ----------------

export type CardSource = 'AI_GENERATED' | 'AI_REVISION' | 'PARENT_EDIT';

export interface CardRow extends SummaryCard {
  source: CardSource;
  revisionInstruction?: string;
  suggestions?: CardSuggestion[];
}

export interface ComplaintRow {
  id: string;
  roomId: string;
  parentId: string;
  teacherId: string;
  studentId: string;
  status: ComplaintStatus;
  originalText: string;
  explanation: AiExplanation | null;
  cards: CardRow[];
  submittedVersion: number | null;
  reply: { content: string; contactMethod: ContactMethod; scheduledAt: string | null; sentAt: string } | null;
  escalation: Escalation | null;
  createdAt: string;
  submittedAt: string | null;
  acknowledgedAt: string | null;
  closedAt: string | null;
  resolvedAt: string | null;
}

export const complaints = new Map<string, ComplaintRow>();

/** 교사에게는 학부모가 '확인하고 보내기'를 누른 뒤부터 보인다 (DB 규칙: 교사 민원 조회 범위) */
export const PRE_SUBMIT: ComplaintStatus[] = ['EXPLAINED', 'CLOSED_BY_EXPLANATION', 'CARD_DRAFTING'];
export const FINISHED: ComplaintStatus[] = ['CLOSED_BY_EXPLANATION', 'RESOLVED'];

export function activeComplaintIdFor(roomId: string, viewer: UserSummary) {
  const c = [...complaints.values()]
    .filter((x) => x.roomId === roomId && !FINISHED.includes(x.status))
    .filter((x) => (viewer.role === 'TEACHER' ? !PRE_SUBMIT.includes(x.status) : x.parentId === viewer.id))
    .sort((a, b) => toMs(b.createdAt) - toMs(a.createdAt))[0];
  return c?.id ?? null;
}

export function changeStatus(c: ComplaintRow, to: ComplaintStatus) {
  const from = c.status;
  c.status = to;
  emit({ event: 'complaint.status_changed', data: { complaintId: c.id, from, to, changedAt: nowIso() } });
}
