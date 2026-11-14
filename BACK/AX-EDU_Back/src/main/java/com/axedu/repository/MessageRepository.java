package com.axedu.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.axedu.entity.Message;

/**
 * 메시지 정렬 키는 (created_at, id). 시드·이관 데이터처럼 ID 가 ULID 가 아니어도 시간순이 맞도록 created_at 을 먼저 본다.
 */
public interface MessageRepository extends JpaRepository<Message, String> {

    Optional<Message> findBySenderIdAndClientMessageId(String senderId, String clientMessageId);

    Optional<Message> findByIdAndRoomId(String id, String roomId);

    /** 최신 메시지부터 */
    @Query("""
            select m from Message m
            where m.roomId = :roomId and m.deletedAt is null
            order by m.createdAt desc, m.id desc
            """)
    List<Message> findLatest(@Param("roomId") String roomId, Limit limit);

    /** 기준 메시지보다 오래된 것, 최신순 */
    @Query("""
            select m from Message m
            where m.roomId = :roomId and m.deletedAt is null
              and (m.createdAt < :at or (m.createdAt = :at and m.id < :id))
            order by m.createdAt desc, m.id desc
            """)
    List<Message> findBefore(@Param("roomId") String roomId, @Param("at") OffsetDateTime at, @Param("id") String id, Limit limit);

    /** 기준 메시지보다 새로운 것, 최신순 */
    @Query("""
            select m from Message m
            where m.roomId = :roomId and m.deletedAt is null
              and (m.createdAt > :at or (m.createdAt = :at and m.id > :id))
            order by m.createdAt desc, m.id desc
            """)
    List<Message> findAfter(@Param("roomId") String roomId, @Param("at") OffsetDateTime at, @Param("id") String id, Limit limit);

    @Query("""
            select count(m) > 0 from Message m
            where m.roomId = :roomId and m.deletedAt is null
              and (m.createdAt < :at or (m.createdAt = :at and m.id < :id))
            """)
    boolean existsOlder(@Param("roomId") String roomId, @Param("at") OffsetDateTime at, @Param("id") String id);
}
