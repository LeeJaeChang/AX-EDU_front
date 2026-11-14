package com.axedu.repository;


import org.springframework.data.jpa.repository.JpaRepository;

import com.axedu.entity.RegulationChunk;

public interface RegulationChunkRepository extends JpaRepository<RegulationChunk, String> {
}
