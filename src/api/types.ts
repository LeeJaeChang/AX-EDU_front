// AXEDU 채팅·민원 소통 API v1 — 데이터 모델 (명세서 3장)

export type Role = 'PARENT' | 'TEACHER' | 'STUDENT' | 'RESPONSE_TEAM' | 'ADMIN' | 'NOTICE';
export type DayOfWeek = 'MON' | 'TUE' | 'WED' | 'THU' | 'FRI' | 'SAT' | 'SUN';

export interface TimeRange {
  /** HH:mm */
  from: string;
  /** HH:mm */
  to: string;
}

export interface UserSummary {
  id: string;
  name: string;
  role: Role;
  studentNumber?: string | null;
  childStudentIds?: string[];
  profileImageUrl?: string | null;
}

export type RoomType = 'DIRECT' | 'NOTICE' | 'GROUP';

export type MessageType =
  | 'TEXT'
  | 'IMAGE'
  | 'FILE'
  | 'SYSTEM'
  | 'COMPLAINT_CARD'
  | 'COMPLAINT_REPLY';

export interface LastMessage {
  id?: string;
  type: MessageType;
  preview: string;
  sentAt: string;
}

export interface ChatRoom {
  id: string;
  type: RoomType;
  title: string;
  counterpart?: UserSummary | null;
  availableHours?: TimeRange | null;
  isWithinAvailableHours?: boolean;
  lastMessage?: LastMessage | null;
  unreadCount?: number;
  muted?: boolean;
  activeComplaintId?: string | null;
  createdAt?: string;
}

/** GET /chat/rooms/{roomId} 응답 = ChatRoom + 상세 화면용 필드 */
export interface ChatRoomDetail extends ChatRoom {
  inputPlaceholder: string;
  canSend: boolean;
  canFileComplaint: boolean;
  canRequestHelp: boolean;
}

export interface Attachment {
  id: string;
  name: string;
  mimeType: string;
  size: number;
  url: string;
  thumbnailUrl?: string | null;
}

export interface Message {
  id: string;
  roomId: string;
  type: MessageType;
  sender: UserSummary | null;
  content: string;
  attachments?: Attachment[];
  complaintId?: string | null;
  clientMessageId?: string | null;
  readByCounterpart?: boolean;
  createdAt: string;
}

export interface ChatSetting {
  availableHours: TimeRange;
  availableDays: DayOfWeek[];
  allowOutsideHoursFor: Role[];
  outsideHoursNotice: string;
  pushEnabled: boolean;
}

export interface Contact extends UserSummary {
  existingRoomId?: string | null;
}

export interface UploadedFile {
  id: string;
  name: string;
  mimeType: string;
  size: number;
  url: string;
  expiresAt: string;
}

// ---------- 응답 봉투 (명세서 2장) ----------

export interface ApiMeta {
  nextCursor?: string | null;
  hasMore?: boolean;
}

export interface Page<T> {
  items: T[];
  nextCursor: string | null;
  hasMore: boolean;
}

export type ErrorCode =
  | 'VALIDATION_ERROR'
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  | 'CHAT_OUTSIDE_AVAILABLE_HOURS'
  | 'NOTICE_ROOM_READ_ONLY'
  | 'NOT_FOUND'
  | 'ROOM_ALREADY_EXISTS'
  | 'INVALID_COMPLAINT_STATUS'
  | 'FILE_TOO_LARGE'
  | 'AI_PROCESSING_FAILED'
  | 'RATE_LIMITED'
  | 'INTERNAL_ERROR'
  | 'NETWORK_ERROR';

// ---------- 요청 바디 ----------

export interface ListRoomsQuery {
  q?: string;
  type?: RoomType;
  cursor?: string;
  limit?: number;
}

export interface ListContactsQuery {
  q?: string;
  role?: 'TEACHER' | 'STUDENT' | 'PARENT';
  grade?: number;
  classNo?: number;
}

export interface ListMessagesQuery {
  before?: string;
  after?: string;
  limit?: number;
}

export interface SendMessageBody {
  clientMessageId: string;
  type: MessageType;
  content: string;
  attachmentIds: string[];
}

export interface RoomSettingsBody {
  muted?: boolean;
  availableHoursOverride?: TimeRange | null;
}

// ---------- WebSocket 이벤트 (명세서 8장) ----------

export type SocketEvent =
  | { event: 'message.created'; data: Message; sentAt: string }
  | {
      event: 'message.read';
      data: { roomId: string; readerId: string; lastReadMessageId: string };
      sentAt: string;
    }
  | { event: 'room.created'; data: ChatRoom; sentAt: string }
  | {
      event: 'room.updated';
      data: {
        roomId: string;
        availableHours: TimeRange | null;
        inputPlaceholder: string;
        canSend: boolean;
      };
      sentAt: string;
    }
  | { event: 'room.unread_changed'; data: { roomId: string; unreadCount: number }; sentAt: string }
  | { event: 'pong'; data?: unknown; sentAt: string }
  | ComplaintSocketEvent;

export type SocketEventName = SocketEvent['event'];

// =========================================================
// 민원·AI 챗봇 (명세서 3.5~3.9, 7장)
// =========================================================

export type ComplaintStatus =
  | 'EXPLAINED'
  | 'CLOSED_BY_EXPLANATION'
  | 'CARD_DRAFTING'
  | 'SUBMITTED'
  | 'TEACHER_REVIEWING'
  | 'IN_CONVERSATION'
  | 'ESCALATED'
  | 'RESOLVED';

export type ComplaintTag = 'LIFE_GUIDANCE' | 'STUDY_EVALUATION' | 'PEER_RELATION' | 'SAFETY' | 'ETC';
export type ContactMethod = 'CALL' | 'MESSAGE' | 'VISIT';
export type SourceType = 'SCHOOL_RULE' | 'NOTICE_LETTER' | 'LAW';
export type Recommendation = 'EXPLAINED' | 'TALK_TO_TEACHER';
export type EscalationReason =
  | 'REPEATED_DEMAND'
  | 'ABUSE'
  | 'OUT_OF_HOURS_PRESSURE'
  | 'BEYOND_AUTHORITY'
  | 'ETC';
export type EscalationStage = 'RESPONSE_TEAM' | 'VICE_PRINCIPAL' | 'PRINCIPAL';
export type EscalationStatus = 'REQUESTED' | 'IN_PROGRESS' | 'RESOLVED';

export interface Regulation {
  id: string;
  sourceType: SourceType;
  title: string;
  excerpt: string;
  documentUrl?: string;
}

export interface AiExplanation {
  empathy: string;
  reason: string;
  alternative: string;
  citations: Regulation[];
  recommendation: Recommendation;
}

export interface SituationItem {
  order: number;
  when: string;
  where: string;
  what: string;
}

export interface SummaryCard {
  version: number;
  basicInfo: { grade: number; classNo: number; receivedAt: string; tags: ComplaintTag[] };
  attribution: string;
  situation: SituationItem[];
  concerns: string[];
  requests: string[];
  preferredContact: { methods: ContactMethod[]; timeWindow: TimeRange | null };
  regulations: Regulation[];
  confirmedByParentAt: string | null;
}

/** 수정 본문에 감정 표현이 다시 들어오면 AI가 순화 문장을 함께 돌려준다 (명세서 7.5) */
export interface CardSuggestion {
  field: 'situation' | 'concerns' | 'requests';
  index: number;
  original: string;
  suggested: string;
}

export interface SummaryCardResult extends SummaryCard {
  suggestions?: CardSuggestion[];
}

export interface TeacherReply {
  content: string;
  contactMethod: ContactMethod;
  scheduledAt: string | null;
  sentAt: string;
}

export interface Escalation {
  id: string;
  reasonTypes: EscalationReason[];
  description: string;
  stage: EscalationStage;
  status: EscalationStatus;
  assignee: UserSummary | null;
  requestedAt: string;
}

export interface Complaint {
  id: string;
  status: ComplaintStatus;
  parent: UserSummary;
  teacher: UserSummary;
  student: UserSummary;
  roomId: string;
  /** 학부모 본인과 민원대응팀 이상만 받는다. 교사 응답에서는 빠진다. */
  originalText?: string;
  aiExplanation?: AiExplanation | null;
  summaryCard: SummaryCard | null;
  teacherReply: TeacherReply | null;
  escalation: Escalation | null;
  createdAt: string;
  submittedAt: string | null;
  closedAt: string | null;
}

export interface CreateComplaintBody {
  roomId: string;
  studentId: string;
  content: string;
  attachmentIds: string[];
}

export type CardEditableFields = Partial<
  Pick<SummaryCard, 'situation' | 'concerns' | 'requests' | 'preferredContact'>
> & { tags?: ComplaintTag[] };

export interface ReplyDraftBody {
  situation: string;
  reason: string;
  nextActions: string;
}

export interface ReplyDraft {
  draft: string;
  sections: { situation: string; reason: string; nextActions: string };
}

export interface ReplyBody {
  content: string;
  contactMethod: ContactMethod;
  scheduledAt: string | null;
}

export interface ReplyResult {
  status: ComplaintStatus;
  messageId: string;
  safeCall?: { reservationId: string; scheduledAt: string };
  visit?: { reservationId: string; scheduledAt: string };
}

export interface EscalationBody {
  reasonTypes: EscalationReason[];
  description: string;
}

export interface ListComplaintsQuery {
  status?: ComplaintStatus[];
  studentId?: string;
  cursor?: string;
  limit?: number;
}

export type ComplaintSocketEvent =
  | {
      event: 'complaint.status_changed';
      data: { complaintId: string; from: ComplaintStatus | null; to: ComplaintStatus; changedAt: string };
      sentAt: string;
    }
  | { event: 'complaint.card_updated'; data: { complaintId: string; version: number }; sentAt: string }
  | { event: 'escalation.created'; data: Escalation; sentAt: string }
  | { event: 'escalation.updated'; data: Escalation; sentAt: string };
