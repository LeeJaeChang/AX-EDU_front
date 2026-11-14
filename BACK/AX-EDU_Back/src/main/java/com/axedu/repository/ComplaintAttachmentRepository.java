package com.axedu.repository;


import org.springframework.data.jpa.repository.JpaRepository;

import com.axedu.entity.ComplaintAttachment;

public interface ComplaintAttachmentRepository extends JpaRepository<ComplaintAttachment, ComplaintAttachment.Pk> {
}
