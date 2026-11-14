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

/** 빠른 대응 필요 여부 — 상담에서 학부모에게 확인한다 */
export type UrgencyLevel = 'URGENT' | 'SOON' | 'NORMAL';

/** 상담 중 학부모의 감정 상태 — AI가 대화를 보고 판단한다 (학부모가 고칠 수 없음) */
export type EmotionLevel = 'CALM' | 'CONCERNED' | 'AGITATED';
export type EmotionTrend = 'RISING' | 'EASING' | 'STEADY';

export interface CardUrgency {
  level: UrgencyLevel;
  /** 예: '학생 안전·건강 관련', '보호자 요청' */
  reason: string | null;
}

export interface ParentEmotion {
  level: EmotionLevel;
  /** 상담 처음과 끝을 비교한 흐름 */
  trend: EmotionTrend;
  /** 원문을 드러내지 않는 신호 분류. 예: '강한 불만 표현', '책임 추궁·외부 신고 언급' */
  signals: string[];
  /** 교사가 첫 연락을 준비할 수 있게 정리한 한두 문장 */
  summary: string;
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
  /** 빠른 대응 필요 여부 */
  urgency: CardUrgency;
  /**
   * 상담 중 학부모 감정 상태 — 교사(와 민원대응팀)에게만 내려간다. 학부모 응답에서는 빠진다.
   * AI 판단이라 학부모가 고칠 수 없다.
   */
  parentEmotion?: ParentEmotion;
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

/**
 * AI 상담 단계. 한 번에 끝내지 않고 여러 대화로 정해 간다.
 * FACTS 사실관계 묻기 → EXPLAIN 규정·법령 설명 → RECIPIENT 연락할 선생님 정하기
 * → CONTACT 연락 방법·시간 → READY 요약 카드 만들 준비 완료
 */
export type ConsultStage = 'FACTS' | 'EXPLAIN' | 'RECIPIENT' | 'CONTACT' | 'READY';

/** 상담사가 제안하는 빠른 답. value가 있으면 그 값으로 처리한다 (예: 선생님 ID, 'MAKE_CARD') */
export interface QuickReply {
  label: string;
  value?: string;
}

export interface ConsultFact {
  label: string;
  value: string;
}

/** AI 상담 대화 한 줄 (학부모에게만 보임, 교사에게는 내려가지 않는다) */
export interface ConsultMessage {
  id: string;
  from: 'PARENT' | 'AI';
  /** 본문. 줄바꿈으로 문단을 나눈다 */
  content: string;
  /** 왜 묻는지 등 보조 설명 */
  note?: string;
  /** 사실관계 정리표 */
  facts?: ConsultFact[];
  /** 규정·법령 설명 */
  explanation?: AiExplanation;
  /** 연락할 선생님을 고른 이유 */
  reasoning?: string[];
  quickReplies?: QuickReply[];
  /** 학부모가 빠른 답을 눌렀을 때의 값 */
  value?: string;
  createdAt: string;
}

/** 민원을 받을 수 있는 선생님. AI가 상담 내용을 보고 한 분을 추천한다. */
export interface ComplaintRecipient {
  teacher: UserSummary;
  /** 예: '3학년 9반 담임', '영어' */
  label: string;
  recommended: boolean;
  reason?: string;
}

export interface Complaint {
  id: string;
  status: ComplaintStatus;
  parent: UserSummary;
  /** 학부모가 '확인하고 보내기'에서 받는 선생님을 고르기 전에는 null */
  teacher: UserSummary | null;
  student: UserSummary;
  /** 제출 시 학부모–교사 DIRECT 방에 연결된다. 제출 전에는 null */
  roomId: string | null;
  /** 학부모 본인과 민원대응팀 이상만 받는다. 교사 응답에서는 빠진다. */
  originalText?: string;
  aiExplanation?: AiExplanation | null;
  /** AI 상담 대화 전체 (학부모 본인만) */
  consultation?: ConsultMessage[];
  /** 지금 상담 단계 (학부모 본인만, 카드 단계 이후는 null) */
  consultStage?: ConsultStage | null;
  /** 상담에서 정한 받는 선생님 (학부모 본인만) */
  proposedTeacher?: UserSummary | null;
  /** 첫 화면에서 고른 사전 분류 (학부모 본인만) */
  category?: ComplaintTag | null;
  summaryCard: SummaryCard | null;
  teacherReply: TeacherReply | null;
  escalation: Escalation | null;
  createdAt: string;
  submittedAt: string | null;
  closedAt: string | null;
}

export interface CreateComplaintBody {
  /** 채팅 목록에서 시작하면 비운다. 받는 선생님은 제출할 때 고른다. */
  roomId?: string | null;
  /** 첫 화면의 사전 분류 선택 (고르지 않고 바로 적으면 생략) */
  category?: ComplaintTag | null;
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
