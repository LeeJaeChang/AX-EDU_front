package com.axedu.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.axedu.common.exception.ApiException;
import com.axedu.common.exception.ErrorCode;
import com.axedu.common.security.AuthUser;
import com.axedu.common.util.IdGenerator;
import com.axedu.common.util.TimeUtil;
import com.axedu.dto.chat.AttachmentResponse;
import com.axedu.dto.chat.LastMessage;
import com.axedu.dto.chat.MessageResponse;
import com.axedu.dto.chat.ReadRequest;
import com.axedu.dto.chat.SendMessageRequest;
import com.axedu.dto.chat.TimeRange;
import com.axedu.dto.realtime.RealtimeEvents;
import com.axedu.dto.user.UserSummary;
import com.axedu.entity.ChatRoom;
import com.axedu.entity.ChatRoomMember;
import com.axedu.entity.Message;
import com.axedu.entity.MessageAttachment;
import com.axedu.entity.StoredFile;
import com.axedu.entity.enums.FilePurpose;
import com.axedu.entity.enums.MessageType;
import com.axedu.entity.enums.RoomType;
import com.axedu.repository.ChatRoomMemberRepository;
import com.axedu.repository.ChatRoomRepository;
import com.axedu.repository.MessageAttachmentRepository;
import com.axedu.repository.MessageRepository;
import com.axedu.websocket.RealtimePublisher;

import lombok.RequiredArgsConstructor;

/** 메시지 목록·보내기·읽음 (계약서 3.7, 3.8, 3.10) */
@Service
@RequiredArgsConstructor
public class MessageService {

    public static final int MAX_CONTENT = 2000;
    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 100;
    /** after 조회는 "그 메시지 이후 전부"(계약서 3.7) — 안전 상한만 둔다 */
    private static final int AFTER_CAP = 1000;

    private final MessageRepository messageRepository;
    private final MessageAttachmentRepository attachmentRepository;
    private final ChatRoomRepository roomRepository;
    private final ChatRoomMemberRepository memberRepository;
    private final RoomContextService roomContextService;
    private final UserService userService;
    private final FileService fileService;
    private final RealtimePublisher publisher;

    // ---------------- 목록 ----------------

    /** 항상 최신순. hasMore = 이 페이지보다 오래된 메시지가 더 있음, nextCursor = 이 페이지에서 가장 오래된 메시지 ID */
    @Transactional(readOnly = true)
    public MessagePage list(AuthUser viewer, String roomId, String before, String after, Integer limit) {
        RoomContext ctx = roomContextService.requireActiveMember(roomId, viewer);
        int size = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

        List<Message> page;
        boolean hasMore;
        if (after != null && !after.isBlank()) {
            Message anchor = anchor(roomId, after);
            page = messageRepository.findAfter(roomId, anchor.getCreatedAt(), anchor.getId(), Limit.of(AFTER_CAP));
            hasMore = false; // 보충 조회: 더 오래된 쪽은 이미 화면에 있음
        } else {
            List<Message> fetched;
            if (before != null && !before.isBlank()) {
                Message anchor = anchor(roomId, before);
                fetched = messageRepository.findBefore(roomId, anchor.getCreatedAt(), anchor.getId(), Limit.of(size + 1));
            } else {
                fetched = messageRepository.findLatest(roomId, Limit.of(size + 1));
            }
            hasMore = fetched.size() > size;
            page = hasMore ? fetched.subList(0, size) : fetched;
        }
        String nextCursor = hasMore && !page.isEmpty() ? page.get(page.size() - 1).getId() : null;
        return new MessagePage(toResponses(page, ctx), nextCursor, hasMore);
    }

    private Message anchor(String roomId, String messageId) {
        return messageRepository.findByIdAndRoomId(messageId, roomId)
                .orElseThrow(() -> ApiException.validation("메시지를 찾을 수 없습니다.", "cursor", "invalid"));
    }

    // ---------------- 보내기 ----------------

    @Transactional
    public SendResult send(AuthUser viewer, String roomId, SendMessageRequest req, String idempotencyKey) {
        RoomContext ctx = roomContextService.requireActiveMember(roomId, viewer);
        if (ctx.room().getType() == RoomType.NOTICE) {
            throw new ApiException(ErrorCode.NOTICE_ROOM_READ_ONLY, "공지형 채팅방에는 메시지를 보낼 수 없습니다.");
        }
        String content = req == null || req.content() == null ? "" : req.content();
        List<String> attachmentIds = req == null || req.attachmentIds() == null ? List.of() : req.attachmentIds();
        if (content.isBlank() && attachmentIds.isEmpty()) {
            throw ApiException.validation("메시지를 입력해 주세요.", "content", "required");
        }
        if (content.length() > MAX_CONTENT) {
            throw ApiException.validation("메시지는 2,000자까지 보낼 수 있습니다.", "content", "max 2000");
        }
        if (!roomContextService.canSend(ctx, viewer.role())) {
            TimeRange h = roomContextService.effectiveHours(ctx);
            throw new ApiException(ErrorCode.CHAT_OUTSIDE_AVAILABLE_HOURS,
                    "대화 가능 시간은 " + h.from() + "~" + h.to() + " 입니다.",
                    Map.of("availableFrom", h.from(), "availableTo", h.to()));
        }

        String clientMessageId = req != null && req.clientMessageId() != null && !req.clientMessageId().isBlank()
                ? req.clientMessageId() : idempotencyKey;
        if (clientMessageId != null && clientMessageId.length() > 64) {
            throw ApiException.validation("요청 형식이 올바르지 않습니다.", "clientMessageId", "max 64");
        }
        if (clientMessageId != null) {
            var dup = messageRepository.findBySenderIdAndClientMessageId(viewer.id(), clientMessageId);
            if (dup.isPresent()) {
                return new SendResult(toResponses(List.of(dup.get()), ctx).get(0), false);
            }
        }

        List<StoredFile> files = fileService.attach(viewer, attachmentIds, FilePurpose.MESSAGE);
        MessageType type = MessageType.TEXT;
        if (!files.isEmpty() && content.isBlank()) {
            type = files.get(0).isImage() ? MessageType.IMAGE : MessageType.FILE;
            content = "";
        }
        Message saved = post(ctx, viewer.id(), type, content, null, null, clientMessageId, files, true);
        return new SendResult(toResponses(List.of(saved), ctx).get(0), true);
    }

    /**
     * 메시지 저장 공통 처리: 첨부 연결, 방 last_* 갱신, 발신자 외 참여자 unreadCount +1, message.created 전송.
     * 시스템 메시지·민원 카드·답변 메시지도 여기로 저장한다.
     */
    @Transactional
    public Message post(RoomContext ctx, String senderId, MessageType type, String content, String complaintId,
                        Long summaryCardId, String clientMessageId, List<StoredFile> files, boolean countUnread) {
        ChatRoom room = ctx.room();
        Message m = new Message();
        m.setId(IdGenerator.newId(IdGenerator.MESSAGE));
        m.setRoomId(room.getId());
        m.setSchoolId(room.getSchoolId());
        m.setSenderId(senderId);
        m.setType(type);
        m.setContent(content == null ? "" : content);
        m.setComplaintId(complaintId);
        m.setSummaryCardId(summaryCardId);
        m.setClientMessageId(clientMessageId);
        m.setCreatedAt(TimeUtil.now());
        messageRepository.save(m);

        short order = 0;
        for (StoredFile f : files == null ? List.<StoredFile>of() : files) {
            attachmentRepository.save(new MessageAttachment(m.getId(), f.getId(), order++));
        }

        room.setLastMessageId(m.getId());
        room.setLastMessageAt(m.getCreatedAt());
        roomRepository.save(room);

        if (countUnread) {
            for (ChatRoomMember member : ctx.activeMembers()) {
                if (!member.getUserId().equals(senderId)) {
                    member.setUnreadCount(member.getUnreadCount() + 1);
                    memberRepository.save(member);
                }
            }
        }
        MessageResponse payload = toResponses(List.of(m), ctx).get(0);
        publisher.publish(ctx.activeMemberIds(), RealtimeEvents.MESSAGE_CREATED, payload);
        return m;
    }

    // ---------------- 읽음 ----------------

    @Transactional
    public void markRead(AuthUser viewer, String roomId, ReadRequest req) {
        RoomContext ctx = roomContextService.requireActiveMember(roomId, viewer);
        String lastReadId = req == null ? null : req.lastReadMessageId();
        ChatRoomMember me = ctx.member(viewer.id());
        if (lastReadId == null || lastReadId.isBlank()) {
            lastReadId = ctx.room().getLastMessageId();
        }
        if (lastReadId != null) {
            Message target = messageRepository.findByIdAndRoomId(lastReadId, roomId)
                    .orElseThrow(() -> ApiException.validation("메시지를 찾을 수 없습니다.", "lastReadMessageId", "invalid"));
            Message current = me.getLastReadMessageId() == null ? null
                    : messageRepository.findById(me.getLastReadMessageId()).orElse(null);
            if (current == null || ORDER.compare(target, current) > 0) {
                me.setLastReadMessageId(target.getId());
            }
        }
        me.setUnreadCount(0);
        memberRepository.save(me);

        publisher.publish(viewer.id(), RealtimeEvents.ROOM_UNREAD_CHANGED, new RealtimeEvents.UnreadChanged(roomId, 0));
        if (lastReadId != null) {
            publisher.publish(ctx.activeMemberIds(), RealtimeEvents.MESSAGE_READ,
                    new RealtimeEvents.MessageRead(roomId, viewer.id(), lastReadId));
        }
    }

    // ---------------- 변환 ----------------

    private static final Comparator<Message> ORDER = Comparator.comparing(Message::getCreatedAt).thenComparing(Message::getId);

    /** Message 목록 → 응답. 보낸 사람·첨부·상대 읽음 여부를 한 번에 읽는다 */
    @Transactional(readOnly = true)
    public List<MessageResponse> toResponses(List<Message> messages, RoomContext ctx) {
        if (messages.isEmpty()) {
            return List.of();
        }
        Map<String, UserSummary> senders = userService.summaries(
                messages.stream().map(Message::getSenderId).filter(Objects::nonNull).toList());

        Map<String, List<AttachmentResponse>> attachments = attachmentsOf(messages.stream().map(Message::getId).toList());

        // 참여자별 마지막 읽은 메시지 (created_at, id)
        Map<String, Message> lastReadByUser = new HashMap<>();
        List<String> readIds = ctx.members().stream().map(ChatRoomMember::getLastReadMessageId).filter(Objects::nonNull).toList();
        Map<String, Message> readMessages = messageRepository.findAllById(readIds).stream()
                .collect(Collectors.toMap(Message::getId, Function.identity()));
        for (ChatRoomMember member : ctx.members()) {
            Message read = member.getLastReadMessageId() == null ? null : readMessages.get(member.getLastReadMessageId());
            if (read != null) {
                lastReadByUser.put(member.getUserId(), read);
            }
        }

        List<MessageResponse> result = new ArrayList<>(messages.size());
        for (Message m : messages) {
            boolean readByCounterpart = lastReadByUser.entrySet().stream()
                    .anyMatch(e -> !e.getKey().equals(m.getSenderId()) && ORDER.compare(e.getValue(), m) >= 0);
            result.add(new MessageResponse(
                    m.getId(),
                    m.getRoomId(),
                    m.getType(),
                    m.getSenderId() == null ? null : senders.get(m.getSenderId()),
                    m.getContent() == null ? "" : m.getContent(),
                    attachments.getOrDefault(m.getId(), List.of()),
                    m.getComplaintId(),
                    m.getClientMessageId(),
                    readByCounterpart,
                    m.getCreatedAt()));
        }
        return result;
    }

    private Map<String, List<AttachmentResponse>> attachmentsOf(Collection<String> messageIds) {
        List<MessageAttachment> links = attachmentRepository.findByMessageIdInOrderBySortOrderAsc(messageIds);
        if (links.isEmpty()) {
            return Map.of();
        }
        Map<String, StoredFile> files = fileService.findAll(links.stream().map(MessageAttachment::getFileId).toList());
        Map<String, List<AttachmentResponse>> result = new HashMap<>();
        for (MessageAttachment link : links) {
            StoredFile f = files.get(link.getFileId());
            if (f != null) {
                result.computeIfAbsent(link.getMessageId(), k -> new ArrayList<>()).add(fileService.toAttachment(f));
            }
        }
        return result;
    }

    /** 목록 미리보기 (계약서 2.2 lastMessage.preview 규칙) */
    @Transactional(readOnly = true)
    public Map<String, LastMessage> lastMessages(Collection<String> messageIds) {
        List<String> ids = messageIds.stream().filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Message> messages = messageRepository.findAllById(ids);
        Map<String, List<AttachmentResponse>> attachments = attachmentsOf(ids);
        Map<String, LastMessage> result = new HashMap<>();
        for (Message m : messages) {
            if (m.getDeletedAt() == null) {
                result.put(m.getId(), new LastMessage(m.getId(), m.getType(), preview(m, attachments.get(m.getId())), m.getCreatedAt()));
            }
        }
        return result;
    }

    public static String preview(Message m, List<AttachmentResponse> attachments) {
        String content = m.getContent() == null ? "" : m.getContent();
        if (m.getType() == MessageType.COMPLAINT_CARD) {
            return "[민원 요약 카드]";
        }
        if (m.getType() == MessageType.COMPLAINT_REPLY) {
            return "[선생님 답변] " + content;
        }
        if (!content.isEmpty()) {
            return content;
        }
        if (attachments != null && !attachments.isEmpty()) {
            return m.getType() == MessageType.IMAGE ? "사진" : "파일: " + attachments.get(0).name();
        }
        return "";
    }

    public record MessagePage(List<MessageResponse> items, String nextCursor, boolean hasMore) {
    }

    public record SendResult(MessageResponse message, boolean created) {
    }

}
