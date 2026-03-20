package com.schoollink.grade;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ExamResultRepository extends JpaRepository<ExamResult, Long> {
    List<ExamResult> findByExamIdAndClassIdAndSubject(Long examId, Long classId, String subject);
    List<ExamResult> findByExamIdAndClassId(Long examId, Long classId);
    List<ExamResult> findByStudentId(Long studentId);
    List<ExamResult> findByStudentIdAndExamId(Long studentId, Long examId);
    Optional<ExamResult> findByExamIdAndStudentIdAndSubject(Long examId, Long studentId, String subject);
    
    // For analysis
    @Query("SELECT r FROM ExamResult r WHERE r.examId = :examId")
    List<ExamResult> findByExamId(Long examId);

    void deleteByExamIdAndClassIdAndSubject(Long examId, Long classId, String subject);
}
