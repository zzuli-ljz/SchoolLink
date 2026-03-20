package com.schoollink.user;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.List;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public boolean existsByUsername(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    public Optional<User> findById(Long id) { return userRepository.findById(id); }

    public void deleteById(Long id) { userRepository.deleteById(id); }

    public List<User> findByClassAndRole(Long classId, Role role) {
        return userRepository.findByClassJoined_IdAndRole(classId, role);
    }
}