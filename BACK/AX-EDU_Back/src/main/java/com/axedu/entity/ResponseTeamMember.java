package com.axedu.entity;

import java.io.Serializable;
import java.time.OffsetDateTime;

import com.axedu.entity.enums.TeamPosition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 학교별 민원대응팀 구성원 (response_team_members) */
@Entity
@Table(name = "response_team_members")
@IdClass(ResponseTeamMember.Pk.class)
@Getter
@Setter
@NoArgsConstructor
public class ResponseTeamMember {

    @Id
    @Column(name = "school_id", length = 32)
    private String schoolId;

    @Id
    @Column(name = "user_id", length = 32)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TeamPosition position = TeamPosition.MEMBER;

    @Column(nullable = false)
    private boolean active = true;

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

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private String schoolId;
        private String userId;
    }
}
