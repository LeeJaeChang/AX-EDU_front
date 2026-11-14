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

/** 최초 민원에 붙인 파일 (complaint_attachments) */
@Entity
@Table(name = "complaint_attachments")
@IdClass(ComplaintAttachment.Pk.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintAttachment {

    @Id
    @Column(name = "complaint_id", length = 32)
    private String complaintId;

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
        private String complaintId;
        private String fileId;
    }
}
