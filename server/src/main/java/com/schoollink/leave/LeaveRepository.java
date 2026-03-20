package com.schoollink.leave;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LeaveRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByClassId(Long classId);

    List<LeaveRequest> findByStudentId(Long studentId);

    List<LeaveRequest> findByClassIdInOrderByCreatedAtDesc(List<Long> classIds);

    void deleteByClassId(Long classId);
}