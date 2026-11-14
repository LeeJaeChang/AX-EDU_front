package com.axedu.entity;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.axedu.common.jpa.DayOfWeekArrayConverter;
import com.axedu.common.jpa.UserRoleArrayConverter;
import com.axedu.entity.enums.DayOfWeekCode;
import com.axedu.entity.enums.UserRole;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 사용자별 '내 채팅 설정' (chat_settings). 행이 없으면 기본값 */
@Entity
@Table(name = "chat_settings")
@Getter
@Setter
@NoArgsConstructor
public class ChatSetting {

    public static final LocalTime DEFAULT_FROM = LocalTime.of(9, 0);
    public static final LocalTime DEFAULT_TO = LocalTime.of(18, 0);
    public static final String DEFAULT_NOTICE = "대화 가능 시간은 {from}~{to} 입니다.";

    @Id
    @Column(name = "user_id", length = 32)
    private String userId;

    @Column(name = "available_from", nullable = false)
    private LocalTime availableFrom = DEFAULT_FROM;

    @Column(name = "available_to", nullable = false)
    private LocalTime availableTo = DEFAULT_TO;

    @Convert(converter = DayOfWeekArrayConverter.class)
    @Column(name = "available_days", nullable = false, length = 100)
    private List<DayOfWeekCode> availableDays = new ArrayList<>(List.of(
            DayOfWeekCode.MON, DayOfWeekCode.TUE, DayOfWeekCode.WED, DayOfWeekCode.THU, DayOfWeekCode.FRI));

    @Convert(converter = UserRoleArrayConverter.class)
    @Column(name = "allow_outside_hours_for", nullable = false, length = 100)
    private List<UserRole> allowOutsideHoursFor = new ArrayList<>(List.of(UserRole.TEACHER));

    /** NULL 이면 기본 템플릿 */
    @Column(name = "outside_hours_notice", length = 200)
    private String outsideHoursNotice;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled = true;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** 저장되지 않은 기본 설정 */
    public static ChatSetting defaults(String userId) {
        ChatSetting s = new ChatSetting();
        s.userId = userId;
        return s;
    }

    public String noticeOrDefault() {
        return outsideHoursNotice == null || outsideHoursNotice.isBlank() ? DEFAULT_NOTICE : outsideHoursNotice;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }
}
