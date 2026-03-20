package com.schoollink.performance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {
    List<Performance> findByStudentIdOrderByDateDesc(Long studentId);
}
