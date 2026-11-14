package com.axedu.repository;


import org.springframework.data.jpa.repository.JpaRepository;

import com.axedu.entity.ComplaintAccessLog;

public interface ComplaintAccessLogRepository extends JpaRepository<ComplaintAccessLog, Long> {
}
