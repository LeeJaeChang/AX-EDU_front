package com.axedu.entity.enums;

import java.time.DayOfWeek;

public enum DayOfWeekCode {
    MON, TUE, WED, THU, FRI, SAT, SUN;

    public static DayOfWeekCode of(DayOfWeek d) {
        return values()[d.getValue() - 1];
    }
}
