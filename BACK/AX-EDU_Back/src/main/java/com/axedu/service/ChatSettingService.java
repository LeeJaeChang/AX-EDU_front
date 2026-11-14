package com.axedu.service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.axedu.common.exception.ApiException;
import com.axedu.common.security.AuthUser;
import com.axedu.common.util.TimeUtil;
import com.axedu.dto.chat.ChatSettingDto;
import com.axedu.dto.chat.TimeRange;
import com.axedu.entity.ChatRoom;
import com.axedu.entity.ChatRoomMember;
import com.axedu.entity.ChatSetting;
import com.axedu.entity.enums.RoomType;
import com.axedu.entity.enums.UserRole;
import com.axedu.repository.ChatRoomMemberRepository;
import com.axedu.repository.ChatRoomRepository;
import com.axedu.repository.ChatSettingRepository;

import lombok.RequiredArgsConstructor;

/** GET·PUT /me/chat-settings (계약서 3.11) */
@Service
@RequiredArgsConstructor
public class ChatSettingService {

    private final ChatSettingRepository settingRepository;
    private final ChatRoomRepository roomRepository;
    private final ChatRoomMemberRepository memberRepository;
    private final RoomContextService roomContextService;
    private final ChatRoomService chatRoomService;

    @Transactional(readOnly = true)
    public ChatSettingDto get(AuthUser viewer) {
        return toDto(settingRepository.findById(viewer.id()).orElseGet(() -> ChatSetting.defaults(viewer.id())));
    }

    /** 교사 외 역할은 pushEnabled 만 반영(에러 아님). 교사가 바꾸면 그 교사가 있는 모든 방(NOTICE 제외)에 room.updated */
    @Transactional
    public ChatSettingDto save(AuthUser viewer, ChatSettingDto req) {
        ChatSetting s = settingRepository.findById(viewer.id()).orElseGet(() -> ChatSetting.defaults(viewer.id()));
        if (req == null) {
            throw ApiException.validation("설정 값을 보내 주세요.", "availableHours", "required");
        }
        if (req.pushEnabled() != null) {
            s.setPushEnabled(req.pushEnabled());
        }
        if (viewer.role() != UserRole.TEACHER) {
            return toDto(settingRepository.save(s));
        }

        TimeRange h = req.availableHours();
        LocalTime from = h == null ? null : TimeUtil.parseHhmm(h.from());
        LocalTime to = h == null ? null : TimeUtil.parseHhmm(h.to());
        if (from == null || to == null || !from.isBefore(to)) {
            throw ApiException.validation("시작 시간이 종료 시간보다 빨라야 합니다.", "availableHours", "from must be before to");
        }
        if (req.outsideHoursNotice() != null && req.outsideHoursNotice().length() > 200) {
            throw ApiException.validation("안내 문구는 200자까지 쓸 수 있어요.", "outsideHoursNotice", "max 200");
        }
        s.setAvailableFrom(from);
        s.setAvailableTo(to);
        if (req.availableDays() != null) {
            s.setAvailableDays(new ArrayList<>(req.availableDays().stream().distinct().toList()));
        }
        if (req.allowOutsideHoursFor() != null) {
            s.setAllowOutsideHoursFor(new ArrayList<>(req.allowOutsideHoursFor().stream().distinct().toList()));
        }
        if (req.outsideHoursNotice() != null) {
            s.setOutsideHoursNotice(req.outsideHoursNotice());
        }
        ChatSetting saved = settingRepository.saveAndFlush(s);

        List<String> roomIds = memberRepository.findByUserIdAndLeftAtIsNull(viewer.id()).stream().map(ChatRoomMember::getRoomId).toList();
        List<ChatRoom> rooms = roomRepository.findAllById(roomIds).stream().filter(r -> r.getType() != RoomType.NOTICE).toList();
        roomContextService.load(rooms).forEach(chatRoomService::publishRoomUpdated);
        return toDto(saved);
    }

    private static ChatSettingDto toDto(ChatSetting s) {
        return new ChatSettingDto(
                new TimeRange(TimeUtil.hhmm(s.getAvailableFrom()), TimeUtil.hhmm(s.getAvailableTo())),
                List.copyOf(s.getAvailableDays()),
                List.copyOf(s.getAllowOutsideHoursFor()),
                s.noticeOrDefault(),
                s.isPushEnabled());
    }
}
