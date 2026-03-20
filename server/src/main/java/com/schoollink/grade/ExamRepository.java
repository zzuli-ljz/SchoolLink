package com.schoollink.grade;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExamRepository extends JpaRepository<Exam, Long> {
    List<Exam> findByGradeLevel(String gradeLevel);
    List<Exam> findBySemester(String semester);
}
