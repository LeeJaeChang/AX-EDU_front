package com.axedu.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.axedu.entity.ComplaintConsultState;

public interface ComplaintConsultStateRepository extends JpaRepository<ComplaintConsultState, String> {

    List<ComplaintConsultState> findByComplaintIdIn(Collection<String> complaintIds);
}
