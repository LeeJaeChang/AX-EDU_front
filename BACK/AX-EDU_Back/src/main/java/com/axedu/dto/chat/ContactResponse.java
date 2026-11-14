package com.axedu.dto.chat;

import java.util.List;

import com.axedu.dto.user.UserSummary;
import com.axedu.entity.enums.UserRole;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Contact = UserSummary + existingRoomId (계약서 2.6) */
public record ContactResponse(
        String id,
        String name,
        UserRole role,
        @JsonInclude(JsonInclude.Include.NON_NULL) String studentNumber,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<String> childStudentIds,
        String profileImageUrl,
        String existingRoomId) {

    public static ContactResponse of(UserSummary u, String existingRoomId) {
        return new ContactResponse(u.id(), u.name(), u.role(), u.studentNumber(), u.childStudentIds(), u.profileImageUrl(), existingRoomId);
    }
}
