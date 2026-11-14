package com.axedu.service;

import java.util.List;
import java.util.Map;

import com.axedu.entity.ChatRoom;
import com.axedu.entity.ChatRoomMember;
import com.axedu.entity.ChatSetting;
import com.axedu.entity.User;

/**
 * 방 하나를 화면 모양으로 바꿀 때 필요한 것들을 한 번에 읽어 둔 것.
 *
 * @param members        나간 사람 포함 전체 참여 기록
 * @param users          참여자 User
 * @param teacher        방의 교사 (없으면 null)
 * @param teacherSetting 교사의 채팅 설정 (행이 없으면 기본값)
 */
public record RoomContext(ChatRoom room, List<ChatRoomMember> members, Map<String, User> users, User teacher,
                          ChatSetting teacherSetting) {

    public List<ChatRoomMember> activeMembers() {
        return members.stream().filter(ChatRoomMember::isActive).toList();
    }

    public List<String> activeMemberIds() {
        return activeMembers().stream().map(ChatRoomMember::getUserId).toList();
    }

    public ChatRoomMember member(String userId) {
        return members.stream().filter(m -> m.getUserId().equals(userId)).findFirst().orElse(null);
    }
}
