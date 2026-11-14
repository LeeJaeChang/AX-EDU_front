package com.axedu.common.jpa;

import java.sql.Types;

import org.hibernate.dialect.PostgreSQLDialect;

/**
 * PostgreSQL 방언 + 스키마 검증(ddl-auto: validate) 보정 한 가지.
 *
 * <p>chat_settings.available_days(day_of_week[])·allow_outside_hours_for(user_role[]) 는 {@link PgArrayConverter} 가
 * "{MON,TUE}" 같은 배열 리터럴 문자열로 읽고 쓴다 (stringtype=unspecified 로 PostgreSQL 이 enum[] 로 변환).
 * 엔티티 쪽 타입은 VARCHAR, DB 쪽은 ARRAY 라 검증만 실패하므로 이 조합을 같은 타입으로 본다. 실제 읽기·쓰기에는 영향 없음.
 */
public class AxeduPostgreSQLDialect extends PostgreSQLDialect {

    @Override
    public boolean equivalentTypes(int typeCode1, int typeCode2) {
        if ((typeCode1 == Types.VARCHAR && typeCode2 == Types.ARRAY) || (typeCode1 == Types.ARRAY && typeCode2 == Types.VARCHAR)) {
            return true;
        }
        return super.equivalentTypes(typeCode1, typeCode2);
    }
}
