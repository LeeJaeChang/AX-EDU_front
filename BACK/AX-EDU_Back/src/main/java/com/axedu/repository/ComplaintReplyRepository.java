package com.axedu.repository;


import org.springframework.data.jpa.repository.JpaRepository;

import com.axedu.entity.ComplaintReply;

public interface ComplaintReplyRepository extends JpaRepository<ComplaintReply, String> {
}
