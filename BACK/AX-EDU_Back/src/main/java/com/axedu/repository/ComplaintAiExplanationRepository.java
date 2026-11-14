package com.axedu.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.axedu.entity.ComplaintAiExplanation;

public interface ComplaintAiExplanationRepository extends JpaRepository<ComplaintAiExplanation, Long> {

    Optional<ComplaintAiExplanation> findFirstByComplaintIdOrderByAttemptDesc(String complaintId);

    Optional<ComplaintAiExplanation> findFirstByComplaintIdAndStatusOrderByAttemptDesc(String complaintId, com.axedu.entity.enums.AiStatus status);
}
