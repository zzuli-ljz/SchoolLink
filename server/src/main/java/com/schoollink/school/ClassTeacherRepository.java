package com.schoollink.school;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassTeacherRepository extends JpaRepository<ClassTeacher, Long> {
    List<ClassTeacher> findBySchoolClass_Id(Long classId);
    List<ClassTeacher> findByTeacher_Id(Long teacherId);
    boolean existsBySchoolClass_IdAndTeacher_Username(Long classId, String username);
    List<ClassTeacher> findBySchoolClass_IdAndTeacher_Username(Long classId, String username);
    void deleteBySchoolClass_Id(Long classId);
}
