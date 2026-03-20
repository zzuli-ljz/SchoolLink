package com.schoollink.dbtest;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SyncTestRepository extends JpaRepository<SyncTest, Long> {
    List<SyncTest> findTop10ByOrderByIdDesc();
}