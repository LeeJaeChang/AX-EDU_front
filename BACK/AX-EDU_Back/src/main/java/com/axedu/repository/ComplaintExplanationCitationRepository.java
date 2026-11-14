package com.axedu.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.axedu.entity.ComplaintExplanationCitation;

public interface ComplaintExplanationCitationRepository extends JpaRepository<ComplaintExplanationCitation, ComplaintExplanationCitation.Pk> {

    List<ComplaintExplanationCitation> findByExplanationIdOrderBySortOrderAsc(Long explanationId);
}
