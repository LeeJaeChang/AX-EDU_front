package com.axedu.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.axedu.entity.TeacherClass;

public interface TeacherClassRepository extends JpaRepository<TeacherClass, Long> {

    List<TeacherClass> findByTeacherIdAndSchoolYear(String teacherId, short schoolYear);

    List<TeacherClass> findBySchoolYearAndGradeAndClassNo(short schoolYear, short grade, short classNo);
}
