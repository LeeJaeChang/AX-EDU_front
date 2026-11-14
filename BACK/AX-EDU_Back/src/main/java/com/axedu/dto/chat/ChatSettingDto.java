package com.axedu.dto.chat;

import java.util.List;

import com.axedu.entity.enums.DayOfWeekCode;
import com.axedu.entity.enums.UserRole;

/** ChatSetting (계약서 2.5). GET 응답 = PUT 요청 = PUT 응답 */
public record ChatSettingDto(
        TimeRange availableHours,
        List<DayOfWeekCode> availableDays,
        List<UserRole> allowOutsideHoursFor,
        String outsideHoursNotice,
        Boolean pushEnabled) {
}
