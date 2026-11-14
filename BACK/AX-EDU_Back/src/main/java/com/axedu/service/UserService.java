package com.axedu.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.axedu.common.exception.ApiException;
import com.axedu.common.security.AuthUser;
import com.axedu.dto.user.UserSummary;
import com.axedu.entity.ParentStudent;
import com.axedu.entity.User;
import com.axedu.entity.enums.UserRole;
import com.axedu.repository.ParentStudentRepository;
import com.axedu.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/** 사용자 조회와 UserSummary 변환 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final ParentStudentRepository parentStudentRepository;

    public User get(String userId) {
        return userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("사용자를 찾을 수 없습니다."));
    }

    /** GET /me — 로그인 사용자 (학부모는 childStudentIds 포함) */
    public UserSummary me(AuthUser auth) {
        return summary(get(auth.id()));
    }

    public List<String> childStudentIds(String parentId) {
        return parentStudentRepository.findByParentId(parentId).stream().map(ParentStudent::getStudentId).sorted().toList();
    }

    public UserSummary summary(User user) {
        if (user == null) {
            return null;
        }
        List<String> children = user.getRole() == UserRole.PARENT ? childStudentIds(user.getId()) : null;
        return toSummary(user, children);
    }

    /** 여러 사용자를 한 번에 UserSummary 로 (학부모 자녀 목록도 한 번에 읽음) */
    public Map<String, UserSummary> summaries(Collection<String> ids) {
        Set<String> unique = new HashSet<>(ids);
        unique.remove(null);
        if (unique.isEmpty()) {
            return Map.of();
        }
        List<User> users = userRepository.findAllById(unique);
        return summariesOf(users);
    }

    public Map<String, UserSummary> summariesOf(Collection<User> users) {
        Map<String, List<String>> children = new HashMap<>();
        for (User u : users) {
            if (u.getRole() == UserRole.PARENT) {
                children.put(u.getId(), childStudentIds(u.getId()));
            }
        }
        return users.stream().collect(Collectors.toMap(User::getId, u -> toSummary(u, children.get(u.getId())), (a, b) -> a));
    }

    private static UserSummary toSummary(User u, List<String> children) {
        return new UserSummary(
                u.getId(),
                u.getName(),
                u.getRole(),
                u.getRole() == UserRole.STUDENT ? u.getStudentNumber() : null,
                u.getRole() == UserRole.PARENT ? (children == null ? List.of() : children) : null,
                u.getProfileImageUrl());
    }
}
