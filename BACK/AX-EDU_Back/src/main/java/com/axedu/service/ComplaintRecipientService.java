package com.axedu.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.axedu.entity.TeacherClass;
import com.axedu.entity.User;
import com.axedu.entity.enums.AssignmentType;
import com.axedu.entity.enums.UserStatus;
import com.axedu.repository.TeacherClassRepository;
import com.axedu.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 민원을 받을 수 있는 선생님 (GET /complaints/{id}/recipients, submit 의 teacherId 검증).
 * 후보: 학생의 이번 학년도 담임 + 교과 교사 (teacher_classes 의 school_year·grade·class_no). 담임이 먼저.
 */
@Service
@RequiredArgsConstructor
public class ComplaintRecipientService {

    private final TeacherClassRepository teacherClassRepository;
    private final UserRepository userRepository;

    /**
     * @param teacherId 교사 ID
     * @param label     예: "3학년 9반 담임 · 수학", "3학년 영어"
     * @param homeroom  담임 여부
     * @param subjects  가르치는 과목 (담임은 다른 반에서 가르치는 과목 포함) — 상담의 선생님 추천 근거
     */
    public record Candidate(String teacherId, String label, boolean homeroom, List<String> subjects) {
    }

    @Transactional(readOnly = true)
    public List<Candidate> candidates(User student) {
        if (student.getGrade() == null || student.getClassNo() == null) {
            return List.of();
        }
        short year = ContactService.schoolYear();
        List<TeacherClass> rows = teacherClassRepository.findBySchoolYearAndGradeAndClassNo(year, student.getGrade(), student.getClassNo());

        // 교사별: 이 반의 담임인지, 이 반에서 가르치는 과목
        Map<String, Boolean> homeroom = new LinkedHashMap<>();
        Map<String, List<String>> subjects = new LinkedHashMap<>();
        for (TeacherClass tc : rows) {
            homeroom.merge(tc.getTeacherId(), tc.getAssignmentType() == AssignmentType.HOMEROOM, Boolean::logicalOr);
            if (tc.getAssignmentType() == AssignmentType.SUBJECT && !tc.getSubject().isBlank()) {
                subjects.computeIfAbsent(tc.getTeacherId(), k -> new ArrayList<>()).add(tc.getSubject());
            }
        }
        Map<String, User> teachers = new LinkedHashMap<>();
        userRepository.findAllById(homeroom.keySet()).stream()
                .filter(u -> u.getSchoolId().equals(student.getSchoolId()) && u.getStatus() == UserStatus.ACTIVE)
                .forEach(u -> teachers.put(u.getId(), u));

        List<Candidate> out = new ArrayList<>();
        for (String id : homeroom.keySet()) {
            if (!teachers.containsKey(id)) {
                continue;
            }
            boolean isHomeroom = homeroom.get(id);
            String label;
            List<String> taught;
            if (isHomeroom) {
                // 담임은 맡은 교과(다른 반 포함)를 덧붙인다: "3학년 9반 담임 · 수학"
                taught = subjects.getOrDefault(id, subjectsAnywhere(id, year));
                label = student.getGrade() + "학년 " + student.getClassNo() + "반 담임"
                        + (taught.isEmpty() ? "" : " · " + String.join(", ", taught));
            } else {
                taught = subjects.getOrDefault(id, List.of());
                label = student.getGrade() + "학년 " + (taught.isEmpty() ? "교과" : String.join(", ", taught));
            }
            out.add(new Candidate(id, label, isHomeroom, List.copyOf(taught)));
        }
        out.sort(Comparator.comparing((Candidate c) -> !c.homeroom()).thenComparing(Candidate::label));
        return out;
    }

    public Optional<Candidate> find(User student, String teacherId) {
        return candidates(student).stream().filter(c -> c.teacherId().equals(teacherId)).findFirst();
    }

    private List<String> subjectsAnywhere(String teacherId, short year) {
        return teacherClassRepository.findByTeacherIdAndSchoolYear(teacherId, year).stream()
                .filter(tc -> tc.getAssignmentType() == AssignmentType.SUBJECT && !tc.getSubject().isBlank())
                .map(TeacherClass::getSubject)
                .distinct()
                .toList();
    }
}
