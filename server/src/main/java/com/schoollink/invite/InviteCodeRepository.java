package com.schoollink.invite;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {
    Optional<InviteCode> findByCode(String code);

    List<InviteCode> findByCreatedByOrderByCreatedAtDesc(String createdBy);

    List<InviteCode> findBySchoolClass_IdAndCreatedByOrderByCreatedAtDesc(Long classId, String createdBy);

    // 按班级查询全部邀请码（用于固定邀请码展示）
    List<InviteCode> findBySchoolClass_IdOrderByCreatedAtAsc(Long classId);

    void deleteBySchoolClass_Id(Long classId);
}