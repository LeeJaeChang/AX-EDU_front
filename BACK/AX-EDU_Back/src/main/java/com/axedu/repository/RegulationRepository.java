package com.axedu.repository;


import org.springframework.data.jpa.repository.JpaRepository;

import com.axedu.entity.Regulation;

public interface RegulationRepository extends JpaRepository<Regulation, String> {
}
