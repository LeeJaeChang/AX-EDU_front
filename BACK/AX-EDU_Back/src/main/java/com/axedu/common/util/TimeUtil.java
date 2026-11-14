package com.axedu.common.util;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public final class TimeUtil {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    public static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private TimeUtil() {
    }

    public static OffsetDateTime now() {
        return OffsetDateTime.now(KST).truncatedTo(ChronoUnit.MILLIS);
    }

    public static OffsetDateTime toKst(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(KST).toOffsetDateTime();
    }

    public static String hhmm(LocalTime t) {
        return t == null ? null : t.format(HH_MM);
    }

    /** "HH:mm" 파싱. 형식이 틀리면 null */
    public static LocalTime parseHhmm(String s) {
        if (s == null || !s.matches("\\d{2}:\\d{2}")) {
            return null;
        }
        try {
            return LocalTime.parse(s, HH_MM);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
