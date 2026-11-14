package com.axedu.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.axedu.entity.ResponseTeamMember;

public interface ResponseTeamMemberRepository extends JpaRepository<ResponseTeamMember, ResponseTeamMember.Pk> {

    List<ResponseTeamMember> findBySchoolIdAndActiveTrue(String schoolId);
}
