package com.axedu.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.axedu.entity.ChatRoomMember;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, ChatRoomMember.Pk> {

    List<ChatRoomMember> findByUserIdAndLeftAtIsNull(String userId);

    List<ChatRoomMember> findByRoomId(String roomId);

    List<ChatRoomMember> findByRoomIdIn(Collection<String> roomIds);

    Optional<ChatRoomMember> findByRoomIdAndUserId(String roomId, String userId);
}
