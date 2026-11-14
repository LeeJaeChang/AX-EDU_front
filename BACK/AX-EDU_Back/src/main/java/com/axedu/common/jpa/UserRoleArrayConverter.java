package com.axedu.common.jpa;

import com.axedu.entity.enums.UserRole;

import jakarta.persistence.Converter;

@Converter
public class UserRoleArrayConverter extends PgArrayConverter<UserRole> {

    public UserRoleArrayConverter() {
        super(UserRole::valueOf);
    }
}
