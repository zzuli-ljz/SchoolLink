package com.schoollink.school;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Long> {
    List<Student> findBySchoolClass_Id(Long classId);

    List<Student> findByParentUser_Id(Long parentUserId);

    List<Student> findByUsername(String username);

    Optional<Student> findBySchoolClass_IdAndNameAndStudentNo(Long classId, String name, String studentNo);

    List<Student> findBySchoolClass_IdIn(List<Long> classIds);
}