package com.axedu.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 메시지–파일 연결 (message_attachments) */
@Entity
@Table(name = "message_attachments")
@IdClass(MessageAttachment.Pk.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageAttachment {

    @Id
    @Column(name = "message_id", length = 32)
    private String messageId;

    @Id
    @Column(name = "file_id", length = 32, unique = true)
    private String fileId;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private String messageId;
        private String fileId;
    }
}
