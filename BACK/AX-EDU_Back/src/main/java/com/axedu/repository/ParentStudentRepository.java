package com.axedu.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.axedu.entity.ParentStudent;

public interface ParentStudentRepository extends JpaRepository<ParentStudent, ParentStudent.Pk> {

    List<ParentStudent> findByParentId(String parentId);

    List<ParentStudent> findByStudentIdIn(Collection<String> studentIds);

    boolean existsByParentIdAndStudentId(String parentId, String studentId);
}
