package com.axedu.entity;

import java.io.Serializable;
import java.time.OffsetDateTime;

import com.axedu.entity.enums.ParentRelation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 학부모–자녀 연결 (parent_students) */
@Entity
@Table(name = "parent_students", indexes = @Index(name = "idx_parent_students_student_id", columnList = "student_id"))
@IdClass(ParentStudent.Pk.class)
@Getter
@Setter
@NoArgsConstructor
public class ParentStudent {

    @Id
    @Column(name = "parent_id", length = 32)
    private String parentId;

    @Id
    @Column(name = "student_id", length = 32)
    private String studentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParentRelation relation = ParentRelation.GUARDIAN;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public ParentStudent(String parentId, String studentId) {
        this.parentId = parentId;
        this.studentId = studentId;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private String parentId;
        private String studentId;
    }
}
