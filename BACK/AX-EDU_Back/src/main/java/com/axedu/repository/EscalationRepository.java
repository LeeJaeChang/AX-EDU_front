package com.axedu.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.axedu.entity.Escalation;

public interface EscalationRepository extends JpaRepository<Escalation, String> {

    Optional<Escalation> findByComplaintId(String complaintId);
}
