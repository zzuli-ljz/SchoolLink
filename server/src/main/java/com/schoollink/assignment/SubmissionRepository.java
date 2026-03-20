package com.schoollink.assignment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    List<Submission> findByAssignmentId(Long assignmentId);

    List<Submission> findByStudentId(Long studentId);

    Submission findByStudentIdAndAssignmentId(Long studentId, Long assignmentId);

    void deleteByAssignmentId(Long assignmentId);
}