package com.axedu.service;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.axedu.common.exception.ApiException;
import com.axedu.common.security.AuthUser;
import com.axedu.common.util.TimeUtil;
import com.axedu.dto.chat.TimeRange;
import com.axedu.entity.ChatRoom;
import com.axedu.entity.ChatRoomMember;
import com.axedu.entity.ChatSetting;
import com.axedu.entity.User;
import com.axedu.entity.enums.DayOfWeekCode;
import com.axedu.entity.enums.RoomType;
import com.axedu.entity.enums.UserRole;
import com.axedu.repository.ChatRoomMemberRepository;
import com.axedu.repository.ChatRoomRepository;
import com.axedu.repository.ChatSettingRepository;
import com.axedu.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 방 읽기 + 대화 가능 시간 규칙 (계약서 2.3, DB 설계서 5장 '대화 가능 시간 판정').
 * 적용 시간: 방 재정의 → 상대 교사 chat_settings → 기본값(09:00~18:00, 평일). KST 기준.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomContextService {

    private final ChatRoomRepository roomRepository;
    private final ChatRoomMemberRepository memberRepository;
    private final ChatSettingRepository settingRepository;
    private final UserRepository userRepository;

    /** 참여 중(나가지 않은) 방만. 아니면 404 (권한 밖 리소스도 404) */
    public RoomContext requireActiveMember(String roomId, AuthUser viewer) {
        ChatRoom room = roomRepository.findById(roomId)
                .filter(r -> r.getSchoolId().equals(viewer.schoolId()))
                .orElseThrow(() -> ApiException.notFound("채팅방을 찾을 수 없습니다."));
        RoomContext ctx = load(List.of(room)).get(0);
        ChatRoomMember me = ctx.member(viewer.id());
        if (me == null || !me.isActive()) {
            throw ApiException.notFound("채팅방을 찾을 수 없습니다.");
        }
        return ctx;
    }

    public RoomContext load(ChatRoom room) {
        return load(List.of(room)).get(0);
    }

    /** 여러 방을 한 번에 읽는다 (목록 화면용) */
    public List<RoomContext> load(Collection<ChatRoom> rooms) {
        if (rooms.isEmpty()) {
            return List.of();
        }
        List<String> roomIds = rooms.stream().map(ChatRoom::getId).toList();
        Map<String, List<ChatRoomMember>> membersByRoom = memberRepository.findByRoomIdIn(roomIds).stream()
                .collect(Collectors.groupingBy(ChatRoomMember::getRoomId));
        Set<String> userIds = new HashSet<>();
        membersByRoom.values().forEach(list -> list.forEach(m -> userIds.add(m.getUserId())));
        Map<String, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        Map<String, User> teacherByRoom = new HashMap<>();
        for (ChatRoom room : rooms) {
            List<ChatRoomMember> members = membersByRoom.getOrDefault(room.getId(), List.of());
            members.stream()
                    .filter(ChatRoomMember::isActive)
                    .sorted(Comparator.comparing(ChatRoomMember::getJoinedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(m -> users.get(m.getUserId()))
                    .filter(u -> u != null && u.getRole() == UserRole.TEACHER)
                    .findFirst()
                    .ifPresent(t -> teacherByRoom.put(room.getId(), t));
        }
        Set<String> teacherIds = teacherByRoom.values().stream().map(User::getId).collect(Collectors.toSet());
        Map<String, ChatSetting> settings = settingRepository.findAllById(teacherIds).stream()
                .collect(Collectors.toMap(ChatSetting::getUserId, Function.identity()));

        List<RoomContext> result = new ArrayList<>();
        for (ChatRoom room : rooms) {
            List<ChatRoomMember> members = membersByRoom.getOrDefault(room.getId(), List.of());
            Map<String, User> roomUsers = new HashMap<>();
            members.forEach(m -> {
                User u = users.get(m.getUserId());
                if (u != null) {
                    roomUsers.put(u.getId(), u);
                }
            });
            User teacher = teacherByRoom.get(room.getId());
            ChatSetting setting = teacher == null ? null
                    : settings.getOrDefault(teacher.getId(), ChatSetting.defaults(teacher.getId()));
            result.add(new RoomContext(room, members, roomUsers, teacher, setting));
        }
        return result;
    }

    // ---------------- 대화 가능 시간 ----------------

    /** 방별 재정의 값 (목록 배지용). 없으면 null */
    public TimeRange overrideHours(ChatRoom room) {
        return room.hasOverride()
                ? new TimeRange(TimeUtil.hhmm(room.getAvailableFromOverride()), TimeUtil.hhmm(room.getAvailableToOverride()))
                : null;
    }

    /** 실제 적용 시간. NOTICE 이거나 교사가 없으면 null */
    public TimeRange effectiveHours(RoomContext ctx) {
        if (ctx.room().getType() == RoomType.NOTICE) {
            return null;
        }
        TimeRange override = overrideHours(ctx.room());
        if (override != null) {
            return override;
        }
        if (ctx.teacher() == null) {
            return null;
        }
        ChatSetting s = ctx.teacherSetting();
        return new TimeRange(TimeUtil.hhmm(s.getAvailableFrom()), TimeUtil.hhmm(s.getAvailableTo()));
    }

    public boolean isWithinHours(RoomContext ctx) {
        TimeRange range = effectiveHours(ctx);
        if (range == null) {
            return true;
        }
        OffsetDateTime now = TimeUtil.now();
        LocalTime t = now.toLocalTime();
        boolean dayOk = ctx.teacherSetting() == null
                || ctx.teacherSetting().getAvailableDays().contains(DayOfWeekCode.of(now.getDayOfWeek()));
        LocalTime from = TimeUtil.parseHhmm(range.from());
        LocalTime to = TimeUtil.parseHhmm(range.to());
        return dayOk && !t.isBefore(from) && t.isBefore(to);
    }

    /** NOTICE=false, 교사=true, 교사 설정 allowOutsideHoursFor 에 내 역할이 있으면 true, 아니면 시간 안일 때만 */
    public boolean canSend(RoomContext ctx, UserRole viewerRole) {
        if (ctx.room().getType() == RoomType.NOTICE) {
            return false;
        }
        if (viewerRole == UserRole.TEACHER) {
            return true;
        }
        if (ctx.teacherSetting() != null && ctx.teacherSetting().getAllowOutsideHoursFor().contains(viewerRole)) {
            return true;
        }
        return isWithinHours(ctx);
    }

    public String inputPlaceholder(RoomContext ctx, UserRole viewerRole) {
        if (ctx.room().getType() == RoomType.NOTICE) {
            return "공지형 채팅방은 답장할 수 없습니다.";
        }
        TimeRange h = effectiveHours(ctx);
        if (h == null || ctx.teacher() == null) {
            return "메시지를 입력하세요.";
        }
        String notice = ctx.teacherSetting().noticeOrDefault().replace("{from}", h.from()).replace("{to}", h.to());
        if (viewerRole != UserRole.TEACHER && !canSend(ctx, viewerRole)) {
            return notice + " 지금은 보낼 수 없어요.";
        }
        return notice;
    }
}
