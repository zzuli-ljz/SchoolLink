package com.schoollink.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    List<User> findByClassJoined_IdAndRole(Long classId, Role role);

    List<User> findByClassJoined_Id(Long classId);

    List<User> findByRole(Role role);
}