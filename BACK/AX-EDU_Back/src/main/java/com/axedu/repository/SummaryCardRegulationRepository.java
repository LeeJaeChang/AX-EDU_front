package com.axedu.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.axedu.entity.SummaryCardRegulation;

public interface SummaryCardRegulationRepository extends JpaRepository<SummaryCardRegulation, SummaryCardRegulation.Pk> {

    List<SummaryCardRegulation> findByCardIdOrderBySortOrderAsc(Long cardId);
}
