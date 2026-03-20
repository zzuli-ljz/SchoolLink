package com.schoollink.assignment;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    List<Assignment> findByClassId(Long classId);
    
    List<Assignment> findByTeacher_Username(String username);

    void deleteByClassId(Long classId);

    void deleteByTeacher_Id(Long teacherId);
}