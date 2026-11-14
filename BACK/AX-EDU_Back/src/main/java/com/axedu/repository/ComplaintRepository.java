package com.axedu.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.axedu.entity.Complaint;
import com.axedu.entity.enums.ComplaintStatus;

public interface ComplaintRepository extends JpaRepository<Complaint, String>, JpaSpecificationExecutor<Complaint> {

    /** 방의 진행 중 민원 (최신순) */
    List<Complaint> findByRoomIdAndStatusNotInOrderByCreatedAtDesc(String roomId, Collection<ComplaintStatus> statuses);

    List<Complaint> findByRoomIdInAndStatusNotIn(Collection<String> roomIds, Collection<ComplaintStatus> statuses);
}
