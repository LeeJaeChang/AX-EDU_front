package com.axedu.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.axedu.entity.MessageAttachment;

public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, MessageAttachment.Pk> {

    List<MessageAttachment> findByMessageIdInOrderBySortOrderAsc(Collection<String> messageIds);
}
