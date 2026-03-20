package com.schoollink.system;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemConfigRepo extends JpaRepository<SystemConfig, Long> {
}
