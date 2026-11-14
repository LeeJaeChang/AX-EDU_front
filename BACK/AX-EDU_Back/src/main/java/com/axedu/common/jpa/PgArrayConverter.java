package com.axedu.common.jpa;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.AttributeConverter;

/**
 * List&lt;Enum&gt; ↔ PostgreSQL 배열 리터럴 "{MON,TUE}".
 * PostgreSQL 에서는 JDBC URL 의 stringtype=unspecified 덕분에 문자열이 day_of_week[]·user_role[] 로 자동 변환된다.
 * H2(로컬)에서는 VARCHAR 로 저장된다.
 */
public abstract class PgArrayConverter<E extends Enum<E>> implements AttributeConverter<List<E>, String> {

    private final Function<String, E> parser;

    protected PgArrayConverter(Function<String, E> parser) {
        this.parser = parser;
    }

    @Override
    public String convertToDatabaseColumn(List<E> attribute) {
        if (attribute == null) {
            return "{}";
        }
        return attribute.stream().map(Enum::name).collect(Collectors.joining(",", "{", "}"));
    }

    @Override
    public List<E> convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return new ArrayList<>();
        }
        String body = dbData.trim();
        if (body.startsWith("{") || body.startsWith("[")) {
            body = body.substring(1, body.length() - 1);
        }
        if (body.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(body.split(","))
                .map(s -> s.trim().replace("\"", ""))
                .map(parser)
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
