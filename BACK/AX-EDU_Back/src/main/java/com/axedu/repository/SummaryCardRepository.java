package com.axedu.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.axedu.entity.SummaryCard;

public interface SummaryCardRepository extends JpaRepository<SummaryCard, Long> {

    Optional<SummaryCard> findFirstByComplaintIdOrderByVersionDesc(String complaintId);

    Optional<SummaryCard> findByComplaintIdAndVersion(String complaintId, int version);
}
