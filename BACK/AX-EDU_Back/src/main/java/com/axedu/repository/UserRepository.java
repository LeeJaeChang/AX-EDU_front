package com.axedu.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.axedu.entity.User;

public interface UserRepository extends JpaRepository<User, String> {

    List<User> findBySchoolIdAndGradeAndClassNoAndRole(String schoolId, Short grade, Short classNo, com.axedu.entity.enums.UserRole role);
}
