package com.axedu.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.axedu.entity.ComplaintConsultMessage;

public interface ComplaintConsultMessageRepository extends JpaRepository<ComplaintConsultMessage, String> {

    List<ComplaintConsultMessage> findByComplaintIdOrderBySeqAsc(String complaintId);

    long countByComplaintId(String complaintId);
}
