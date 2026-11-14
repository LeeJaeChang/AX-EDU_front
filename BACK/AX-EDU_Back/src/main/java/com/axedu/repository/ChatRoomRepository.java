package com.axedu.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.axedu.entity.ChatRoom;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, String> {

    Optional<ChatRoom> findByDirectKey(String directKey);
}
