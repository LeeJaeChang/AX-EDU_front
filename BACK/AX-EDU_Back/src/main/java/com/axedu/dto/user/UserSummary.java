package com.axedu.dto.user;

import java.util.List;

import com.axedu.entity.enums.UserRole;
import com.fasterxml.jackson.annotation.JsonInclude;

/** UserSummary (계약서 2.1). studentNumber 는 학생만, childStudentIds 는 학부모만 키를 넣는다 */
public record UserSummary(
        String id,
        String name,
        UserRole role,
        @JsonInclude(JsonInclude.Include.NON_NULL) String studentNumber,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<String> childStudentIds,
        String profileImageUrl) {
}
