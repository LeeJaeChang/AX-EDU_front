package com.axedu.entity;

import java.time.OffsetDateTime;

import com.axedu.entity.enums.UserRole;
import com.axedu.entity.enums.UserStatus;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 슈퍼스쿨 사용자의 사본 (users) */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_school_id_role", columnList = "school_id, role"),
        @Index(name = "idx_users_school_id_grade_class_no", columnList = "school_id, grade, class_no"),
        @Index(name = "idx_users_school_id_name", columnList = "school_id, name")})
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "school_id", length = 32, nullable = false)
    private String schoolId;

    @Column(name = "superschool_user_id", length = 64, nullable = false, unique = true)
    private String superschoolUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(length = 50, nullable = false)
    private String name;

    private Short grade;

    @Column(name = "class_no")
    private Short classNo;

    @JdbcTypeCode(SqlTypes.CHAR) // CHAR(5): 학번 5자리
    @Column(name = "student_number", length = 5)
    private String studentNumber;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "synced_at")
    private OffsetDateTime syncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
