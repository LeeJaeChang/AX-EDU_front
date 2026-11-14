package com.axedu.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.axedu.common.exception.ApiException;
import com.axedu.common.exception.ErrorCode;
import com.axedu.common.security.AuthUser;
import com.axedu.common.util.IdGenerator;
import com.axedu.common.util.TimeUtil;
import com.axedu.dto.chat.ChatRoomDetailResponse;
import com.axedu.dto.chat.ChatRoomResponse;
import com.axedu.dto.chat.LastMessage;
import com.axedu.dto.chat.TimeRange;
import com.axedu.dto.chat.UpdateRoomSettingsRequest;
import com.axedu.dto.realtime.RealtimeEvents;
import com.axedu.dto.user.UserSummary;
import com.axedu.entity.ChatRoom;
import com.axedu.entity.ChatRoomMember;
import com.axedu.entity.Complaint;
import com.axedu.entity.User;
import com.axedu.entity.enums.ComplaintStatus;
import com.axedu.entity.enums.MessageType;
import com.axedu.entity.enums.RoomType;
import com.axedu.entity.enums.UserRole;
import com.axedu.repository.ChatRoomMemberRepository;
import com.axedu.repository.ChatRoomRepository;
import com.axedu.repository.ComplaintRepository;
import com.axedu.websocket.RealtimePublisher;

import lombok.RequiredArgsConstructor;

/** 대화 목록·방 만들기·방 상세·방 설정·나가기 (계약서 3.1, 3.3~3.6) */
@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final ChatRoomRepository roomRepository;
    private final ChatRoomMemberRepository memberRepository;
    private final ComplaintRepository complaintRepository;
    private final RoomContextService roomContextService;
    private final MessageService messageService;
    private final ContactService contactService;
    private final UserService userService;
    private final RealtimePublisher publisher;

    // ---------------- 목록 ----------------

    /**
     * lastMessage.sentAt 내림차순. q 는 방 제목·상대 이름·학번 부분 일치.
     * TODO: 방이 아주 많아지면 DB 정렬·키셋 페이지네이션으로 바꾼다 (지금은 내 방 전체를 읽어 메모리에서 정렬)
     */
    @Transactional(readOnly = true)
    public RoomPage list(AuthUser viewer, String q, RoomType type, String cursor, Integer limit) {
        int size = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        int offset = parseCursor(cursor);

        List<String> roomIds = memberRepository.findByUserIdAndLeftAtIsNull(viewer.id()).stream()
                .map(ChatRoomMember::getRoomId).toList();
        List<ChatRoom> rooms = roomRepository.findAllById(roomIds).stream()
                .filter(r -> type == null || r.getType() == type)
                .toList();
        List<ChatRoomResponse> views = views(roomContextService.load(rooms), viewer);

        String keyword = q == null ? null : q.trim().toLowerCase(Locale.ROOT);
        List<ChatRoomResponse> filtered = views.stream()
                .filter(v -> keyword == null || keyword.isEmpty() || searchText(v).contains(keyword))
                .sorted(Comparator.comparing((ChatRoomResponse v) -> v.lastMessage() == null ? v.createdAt() : v.lastMessage().sentAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(ChatRoomResponse::id))
                .toList();

        int from = Math.min(offset, filtered.size());
        int to = Math.min(from + size, filtered.size());
        String next = to < filtered.size() ? String.valueOf(to) : null;
        return new RoomPage(filtered.subList(from, to), next);
    }

    private static String searchText(ChatRoomResponse v) {
        UserSummary c = v.counterpart();
        return String.join(" ", Objects.toString(v.title(), ""),
                c == null ? "" : Objects.toString(c.name(), ""),
                c == null ? "" : Objects.toString(c.studentNumber(), "")).toLowerCase(Locale.ROOT);
    }

    private static int parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(cursor));
        } catch (NumberFormatException e) {
            throw ApiException.validation("목록 위치가 올바르지 않습니다.", "cursor", "invalid");
        }
    }

    // ---------------- 방 만들기 ----------------

    /** 이미 방이 있으면 (나갔던 사람을 다시 참여시킨 뒤) 409 ROOM_ALREADY_EXISTS — 재참여는 커밋되어야 하므로 noRollbackFor */
    @Transactional(noRollbackFor = ApiException.class)
    public ChatRoomResponse create(AuthUser viewer, String counterpartId) {
        if (counterpartId == null || counterpartId.isBlank()) {
            throw ApiException.validation("대화 상대를 골라 주세요.", "counterpartId", "required");
        }
        Optional<ChatRoom> existing = roomRepository.findByDirectKey(ChatRoom.directKeyOf(viewer.id(), counterpartId));
        if (existing.isPresent()) {
            rejoinLeftMembers(existing.get());
            throw new ApiException(ErrorCode.ROOM_ALREADY_EXISTS, "이미 대화 중인 상대입니다.", Map.of("roomId", existing.get().getId()));
        }
        if (!contactService.canContact(viewer, counterpartId)) {
            throw ApiException.forbidden("대화할 수 없는 상대입니다.");
        }
        RoomContext ctx = createDirect(viewer, counterpartId);
        return view(ctx, ctx.users().get(viewer.id()));
    }

    /**
     * 두 사람의 DIRECT 방을 돌려준다. 없으면 만들고 상대에게 room.created, 나갔던 사람은 다시 참여시킨다.
     * 민원 제출(받는 선생님과의 방에 카드 올리기)에서 쓴다. 상대가 대화 가능한지는 호출하는 쪽이 확인한다.
     */
    @Transactional
    public ChatRoom ensureDirect(AuthUser viewer, String counterpartId) {
        Optional<ChatRoom> existing = roomRepository.findByDirectKey(ChatRoom.directKeyOf(viewer.id(), counterpartId));
        if (existing.isPresent()) {
            ChatRoom room = existing.get();
            List<String> rejoined = rejoinLeftMembers(room);
            if (!rejoined.isEmpty()) {
                RoomContext ctx = roomContextService.load(room);
                for (String userId : rejoined) {
                    publisher.publish(userId, RealtimeEvents.ROOM_CREATED, view(ctx, ctx.users().get(userId)));
                }
            }
            return room;
        }
        return createDirect(viewer, counterpartId).room();
    }

    /** 나갔던 참여자를 다시 참여시키고 그 사용자 ID 목록을 돌려준다 */
    private List<String> rejoinLeftMembers(ChatRoom room) {
        List<String> rejoined = new ArrayList<>();
        for (ChatRoomMember m : memberRepository.findByRoomId(room.getId())) {
            if (!m.isActive()) {
                m.setLeftAt(null);
                m.setJoinedAt(TimeUtil.now());
                m.setUnreadCount(0);
                memberRepository.save(m);
                rejoined.add(m.getUserId());
            }
        }
        return rejoined;
    }

    private RoomContext createDirect(AuthUser viewer, String counterpartId) {
        ChatRoom room = new ChatRoom();
        room.setId(IdGenerator.newId(IdGenerator.ROOM));
        room.setSchoolId(viewer.schoolId());
        room.setType(RoomType.DIRECT);
        room.setDirectKey(ChatRoom.directKeyOf(viewer.id(), counterpartId));
        room.setCreatedAt(TimeUtil.now());
        roomRepository.save(room);
        memberRepository.save(new ChatRoomMember(room.getId(), viewer.id()));
        memberRepository.save(new ChatRoomMember(room.getId(), counterpartId));
        memberRepository.flush();

        RoomContext ctx = roomContextService.load(room);
        messageService.post(ctx, null, MessageType.SYSTEM, "채팅방이 생성되었습니다.", null, null, null, List.of(), false);

        User counterpart = ctx.users().get(counterpartId);
        publisher.publish(counterpartId, RealtimeEvents.ROOM_CREATED, view(ctx, counterpart));
        return ctx;
    }

    // ---------------- 상세 ----------------

    @Transactional(readOnly = true)
    public ChatRoomDetailResponse detail(AuthUser viewer, String roomId) {
        RoomContext ctx = roomContextService.requireActiveMember(roomId, viewer);
        User me = ctx.users().get(viewer.id());
        ChatRoomResponse base = view(ctx, me);
        UserSummary cp = base.counterpart();
        boolean canFileComplaint = viewer.role() == UserRole.PARENT && ctx.room().getType() == RoomType.DIRECT
                && cp != null && cp.role() == UserRole.TEACHER;
        boolean canRequestHelp = viewer.role() == UserRole.TEACHER && base.activeComplaintId() != null;
        return ChatRoomDetailResponse.of(base,
                roomContextService.effectiveHours(ctx),
                roomContextService.inputPlaceholder(ctx, viewer.role()),
                roomContextService.canSend(ctx, viewer.role()),
                canFileComplaint,
                canRequestHelp);
    }

    // ---------------- 방 설정 ----------------

    @Transactional
    public ChatRoomResponse updateSettings(AuthUser viewer, String roomId, UpdateRoomSettingsRequest req) {
        RoomContext ctx = roomContextService.requireActiveMember(roomId, viewer);
        if (req == null) {
            return view(ctx, ctx.users().get(viewer.id()));
        }
        if (req.getMuted() != null) {
            ChatRoomMember me = ctx.member(viewer.id());
            me.setMuted(req.getMuted());
            memberRepository.save(me);
        }
        if (req.isAvailableHoursOverridePresent()) {
            if (viewer.role() != UserRole.TEACHER) {
                throw ApiException.forbidden("대화 가능 시간은 교사만 바꿀 수 있습니다.");
            }
            TimeRange h = req.getAvailableHoursOverride();
            ChatRoom room = ctx.room();
            if (h == null) {
                room.setAvailableFromOverride(null);
                room.setAvailableToOverride(null);
            } else {
                var from = TimeUtil.parseHhmm(h.from());
                var to = TimeUtil.parseHhmm(h.to());
                if (from == null || to == null || !from.isBefore(to)) {
                    throw ApiException.validation("시작 시간이 종료 시간보다 빨라야 합니다.", "availableHoursOverride", "from must be before to");
                }
                room.setAvailableFromOverride(from);
                room.setAvailableToOverride(to);
            }
            roomRepository.save(room);
            publishRoomUpdated(ctx);
        }
        return view(ctx, ctx.users().get(viewer.id()));
    }

    /** room.updated — 받는 사람 기준 placeholder·canSend. availableHours 는 방 재정의 값(목록 배지와 같은 의미) */
    public void publishRoomUpdated(RoomContext ctx) {
        if (ctx.room().getType() == RoomType.NOTICE) {
            return;
        }
        TimeRange override = roomContextService.overrideHours(ctx.room());
        for (ChatRoomMember m : ctx.activeMembers()) {
            User u = ctx.users().get(m.getUserId());
            if (u == null) {
                continue;
            }
            publisher.publish(u.getId(), RealtimeEvents.ROOM_UPDATED, new RealtimeEvents.RoomUpdated(
                    ctx.room().getId(), override, roomContextService.inputPlaceholder(ctx, u.getRole()),
                    roomContextService.canSend(ctx, u.getRole())));
        }
    }

    // ---------------- 나가기 ----------------

    @Transactional
    public void leave(AuthUser viewer, String roomId) {
        RoomContext ctx = roomContextService.requireActiveMember(roomId, viewer);
        List<Complaint> open = complaintRepository.findByRoomIdAndStatusNotInOrderByCreatedAtDesc(roomId, ComplaintStatus.FINISHED);
        if (activeComplaintId(open, viewer.id(), viewer.role()) != null) {
            throw new ApiException(ErrorCode.INVALID_COMPLAINT_STATUS, "민원이 진행 중인 방은 나갈 수 없습니다.");
        }
        ChatRoomMember me = ctx.member(viewer.id());
        me.setLeftAt(TimeUtil.now());
        me.setUnreadCount(0);
        memberRepository.save(me);
        // 남은 사람에게만 시스템 메시지
        messageService.post(ctx, null, MessageType.SYSTEM, viewer.name() + "님이 나갔습니다.", null, null, null, List.of(), true);
    }

    // ---------------- 변환 ----------------

    public ChatRoomResponse view(RoomContext ctx, User viewer) {
        return views(List.of(ctx), viewer).get(0);
    }

    private List<ChatRoomResponse> views(List<RoomContext> contexts, AuthUser viewer) {
        User me = userService.get(viewer.id());
        return views(contexts, me);
    }

    /** 받는 사람 기준 ChatRoom 모양 (계약서 2.2) */
    public List<ChatRoomResponse> views(List<RoomContext> contexts, User viewer) {
        if (contexts.isEmpty()) {
            return List.of();
        }
        Map<String, LastMessage> lastMessages = messageService.lastMessages(
                contexts.stream().map(c -> c.room().getLastMessageId()).filter(Objects::nonNull).toList());
        Map<String, List<Complaint>> openComplaints = complaintRepository
                .findByRoomIdInAndStatusNotIn(contexts.stream().map(c -> c.room().getId()).toList(), ComplaintStatus.FINISHED)
                .stream().collect(Collectors.groupingBy(Complaint::getRoomId));

        List<String> counterpartIds = new ArrayList<>();
        for (RoomContext ctx : contexts) {
            User cp = counterpartOf(ctx, viewer.getId());
            if (cp != null) {
                counterpartIds.add(cp.getId());
            }
        }
        Map<String, UserSummary> summaries = userService.summaries(counterpartIds);

        List<ChatRoomResponse> result = new ArrayList<>();
        for (RoomContext ctx : contexts) {
            ChatRoom room = ctx.room();
            User cp = counterpartOf(ctx, viewer.getId());
            UserSummary cpSummary = cp == null ? null : summaries.get(cp.getId());
            ChatRoomMember me = ctx.member(viewer.getId());
            result.add(new ChatRoomResponse(
                    room.getId(),
                    room.getType(),
                    title(room, cpSummary),
                    cpSummary,
                    roomContextService.overrideHours(room),
                    roomContextService.isWithinHours(ctx),
                    room.getLastMessageId() == null ? null : lastMessages.get(room.getLastMessageId()),
                    me == null ? 0 : me.getUnreadCount(),
                    me != null && me.isMuted(),
                    activeComplaintId(openComplaints.getOrDefault(room.getId(), List.of()), viewer.getId(), viewer.getRole()),
                    room.getCreatedAt()));
        }
        return result;
    }

    /** DIRECT=상대, NOTICE=공지 계정, GROUP=null */
    private static User counterpartOf(RoomContext ctx, String viewerId) {
        ChatRoom room = ctx.room();
        if (room.getType() == RoomType.GROUP) {
            return null;
        }
        if (room.getType() == RoomType.NOTICE) {
            if (room.getOwnerId() != null && ctx.users().containsKey(room.getOwnerId())) {
                return ctx.users().get(room.getOwnerId());
            }
            return ctx.users().values().stream().filter(u -> u.getRole() == UserRole.NOTICE).findFirst().orElse(null);
        }
        return ctx.members().stream()
                .map(ChatRoomMember::getUserId)
                .filter(id -> !id.equals(viewerId))
                .map(id -> ctx.users().get(id))
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
    }

    /** DIRECT: "이름 학번 [역할]", NOTICE: 공지 계정 이름, GROUP: 방 제목 */
    private static String title(ChatRoom room, UserSummary cp) {
        if (room.getType() == RoomType.GROUP) {
            return room.getTitle() == null ? "" : room.getTitle();
        }
        if (cp == null) {
            return room.getTitle() == null ? "" : room.getTitle();
        }
        if (room.getType() == RoomType.NOTICE) {
            return cp.name();
        }
        String label = switch (cp.role()) {
            case STUDENT -> "[학생]";
            case PARENT -> "[학부모]";
            case TEACHER -> "[교사]";
            default -> null;
        };
        StringBuilder sb = new StringBuilder(cp.name());
        if (cp.studentNumber() != null) {
            sb.append(' ').append(cp.studentNumber());
        }
        if (label != null) {
            sb.append(' ').append(label);
        }
        return sb.toString();
    }

    /**
     * 종료되지 않은 민원 중 최신 1건. 교사에게는 SUBMITTED 이후 민원만, 그 외에는 본인이 쓴 민원만 (계약서 2.2)
     */
    public static String activeComplaintId(List<Complaint> openComplaints, String viewerId, UserRole role) {
        return openComplaints.stream()
                .filter(c -> !c.getStatus().isFinished())
                .filter(c -> role == UserRole.TEACHER ? !c.getStatus().isPreSubmit() : c.getParentId().equals(viewerId))
                .max(Comparator.comparing(Complaint::getCreatedAt))
                .map(Complaint::getId)
                .orElse(null);
    }

    public record RoomPage(List<ChatRoomResponse> items, String nextCursor) {
    }
}
