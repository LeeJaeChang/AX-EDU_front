package com.axedu.repository;


import org.springframework.data.jpa.repository.JpaRepository;

import com.axedu.entity.EscalationLog;

public interface EscalationLogRepository extends JpaRepository<EscalationLog, Long> {
}
