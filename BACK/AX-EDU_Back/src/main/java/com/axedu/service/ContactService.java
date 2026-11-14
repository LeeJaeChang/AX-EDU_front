package com.axedu.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.axedu.common.security.AuthUser;
import com.axedu.common.util.TimeUtil;
import com.axedu.dto.chat.ContactResponse;
import com.axedu.dto.user.UserSummary;
import com.axedu.entity.ChatRoom;
import com.axedu.entity.ChatRoomMember;
import com.axedu.entity.ParentStudent;
import com.axedu.entity.TeacherClass;
import com.axedu.entity.User;
import com.axedu.entity.enums.UserRole;
import com.axedu.entity.enums.UserStatus;
import com.axedu.repository.ChatRoomMemberRepository;
import com.axedu.repository.ChatRoomRepository;
import com.axedu.repository.ParentStudentRepository;
import com.axedu.repository.TeacherClassRepository;
import com.axedu.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 새 대화 상대 (계약서 3.2).
 * 교사 → 담당 학급(담임·교과) 학생과 그 학부모, 학부모 → 자녀의 담임·교과 교사, 학생 → 자기 학급 교사.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContactService {

    private final UserRepository userRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final TeacherClassRepository teacherClassRepository;
    private final ChatRoomRepository roomRepository;
    private final ChatRoomMemberRepository memberRepository;
    private final UserService userService;

    public List<ContactResponse> list(AuthUser viewer, String q, UserRole role, Integer grade, Integer classNo) {
        List<User> candidates = reachableUsers(viewer);
        Map<String, List<User>> childrenOfParent = childrenOf(candidates);
        String keyword = q == null ? null : q.trim();

        List<User> filtered = candidates.stream()
                .filter(u -> role == null || u.getRole() == role)
                .filter(u -> matchesClass(u, grade, classNo, childrenOfParent))
                .filter(u -> keyword == null || keyword.isEmpty() || u.getName().contains(keyword)
                        || (u.getStudentNumber() != null && u.getStudentNumber().contains(keyword)))
                .sorted(Comparator.comparing((User u) -> roleOrder(u.getRole()))
                        .thenComparing(u -> Objects.toString(u.getStudentNumber(), ""))
                        .thenComparing(User::getName))
                .toList();

        Map<String, UserSummary> summaries = userService.summariesOf(filtered);
        List<ContactResponse> result = new ArrayList<>();
        for (User u : filtered) {
            result.add(ContactResponse.of(summaries.get(u.getId()), existingRoomId(viewer.id(), u.getId())));
        }
        return result;
    }

    public boolean canContact(AuthUser viewer, String counterpartId) {
        return reachableUsers(viewer).stream().anyMatch(u -> u.getId().equals(counterpartId));
    }

    /** 내가 참여 중인 1:1 방 ID */
    private String existingRoomId(String viewerId, String otherId) {
        return roomRepository.findByDirectKey(ChatRoom.directKeyOf(viewerId, otherId))
                .filter(r -> memberRepository.findByRoomIdAndUserId(r.getId(), viewerId).map(ChatRoomMember::isActive).orElse(false))
                .map(ChatRoom::getId)
                .orElse(null);
    }

    /** 역할별 대화 가능 범위 */
    private List<User> reachableUsers(AuthUser viewer) {
        short year = schoolYear();
        Set<String> ids = new LinkedHashSet<>();
        switch (viewer.role()) {
            case TEACHER -> {
                List<String> studentIds = new ArrayList<>();
                for (TeacherClass tc : teacherClassRepository.findByTeacherIdAndSchoolYear(viewer.id(), year)) {
                    userRepository.findBySchoolIdAndGradeAndClassNoAndRole(viewer.schoolId(), tc.getGrade(), tc.getClassNo(), UserRole.STUDENT)
                            .forEach(s -> studentIds.add(s.getId()));
                }
                ids.addAll(studentIds);
                if (!studentIds.isEmpty()) {
                    parentStudentRepository.findByStudentIdIn(studentIds).forEach(ps -> ids.add(ps.getParentId()));
                }
            }
            case PARENT -> {
                List<String> childIds = parentStudentRepository.findByParentId(viewer.id()).stream().map(ParentStudent::getStudentId).toList();
                for (User child : userRepository.findAllById(childIds)) {
                    ids.addAll(teachersOf(child, year));
                }
            }
            case STUDENT -> ids.addAll(teachersOf(userService.get(viewer.id()), year));
            default -> {
                // 대응팀·관리자·공지 계정은 새 대화 상대 없음
            }
        }
        ids.remove(viewer.id());
        return userRepository.findAllById(ids).stream()
                .filter(u -> u.getSchoolId().equals(viewer.schoolId()) && u.getStatus() == UserStatus.ACTIVE)
                .toList();
    }

    private List<String> teachersOf(User student, short year) {
        if (student.getGrade() == null || student.getClassNo() == null) {
            return List.of();
        }
        return teacherClassRepository.findBySchoolYearAndGradeAndClassNo(year, student.getGrade(), student.getClassNo())
                .stream().map(TeacherClass::getTeacherId).distinct().toList();
    }

    private Map<String, List<User>> childrenOf(List<User> users) {
        List<String> parentIds = users.stream().filter(u -> u.getRole() == UserRole.PARENT).map(User::getId).toList();
        if (parentIds.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> idMap = new HashMap<>();
        for (String pid : parentIds) {
            idMap.put(pid, parentStudentRepository.findByParentId(pid).stream().map(ParentStudent::getStudentId).toList());
        }
        Set<String> allChildIds = new LinkedHashSet<>();
        idMap.values().forEach(allChildIds::addAll);
        Map<String, User> children = new LinkedHashMap<>();
        userRepository.findAllById(allChildIds).forEach(u -> children.put(u.getId(), u));
        Map<String, List<User>> result = new HashMap<>();
        idMap.forEach((pid, cids) -> result.put(pid, cids.stream().map(children::get).filter(Objects::nonNull).toList()));
        return result;
    }

    /** grade·classNo 필터: 학생은 자기 학급, 학부모는 자녀 학급 기준. 교사는 거르지 않음 */
    private static boolean matchesClass(User u, Integer grade, Integer classNo, Map<String, List<User>> childrenOfParent) {
        if (grade == null && classNo == null) {
            return true;
        }
        if (u.getRole() == UserRole.STUDENT) {
            return sameClass(u, grade, classNo);
        }
        if (u.getRole() == UserRole.PARENT) {
            return childrenOfParent.getOrDefault(u.getId(), List.of()).stream().anyMatch(c -> sameClass(c, grade, classNo));
        }
        return true;
    }

    private static boolean sameClass(User s, Integer grade, Integer classNo) {
        return (grade == null || (s.getGrade() != null && s.getGrade().intValue() == grade))
                && (classNo == null || (s.getClassNo() != null && s.getClassNo().intValue() == classNo));
    }

    private static int roleOrder(UserRole role) {
        return switch (role) {
            case TEACHER -> 0;
            case STUDENT -> 1;
            case PARENT -> 2;
            default -> 3;
        };
    }

    /** 학년도: 3월 시작 (1·2월은 전년도) */
    public static short schoolYear() {
        LocalDate today = TimeUtil.now().toLocalDate();
        return (short) (today.getMonthValue() < 3 ? today.getYear() - 1 : today.getYear());
    }
}
