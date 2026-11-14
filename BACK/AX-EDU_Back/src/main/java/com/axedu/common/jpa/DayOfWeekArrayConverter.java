package com.axedu.common.jpa;

import com.axedu.entity.enums.DayOfWeekCode;

import jakarta.persistence.Converter;

@Converter
public class DayOfWeekArrayConverter extends PgArrayConverter<DayOfWeekCode> {

    public DayOfWeekArrayConverter() {
        super(DayOfWeekCode::valueOf);
    }
}
