package com.axedu.entity;

import java.time.OffsetDateTime;

import com.axedu.entity.enums.AssignmentType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 교사 담당 학급·교과 (teacher_classes) */
@Entity
@Table(name = "teacher_classes",
        uniqueConstraints = @UniqueConstraint(name = "uq_teacher_classes_teacher_year_class_subject",
                columnNames = {"teacher_id", "school_year", "grade", "class_no", "subject"}),
        indexes = @Index(name = "idx_teacher_classes_school_year_grade_class_no", columnList = "school_year, grade, class_no"))
@Getter
@Setter
@NoArgsConstructor
public class TeacherClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_id", length = 32, nullable = false)
    private String teacherId;

    @Column(name = "school_year", nullable = false)
    private short schoolYear;

    @Column(nullable = false)
    private short grade;

    @Column(name = "class_no", nullable = false)
    private short classNo;

    @Column(length = 50, nullable = false)
    private String subject = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_type", nullable = false, length = 20)
    private AssignmentType assignmentType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
