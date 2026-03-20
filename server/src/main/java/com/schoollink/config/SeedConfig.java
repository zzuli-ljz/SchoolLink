package com.schoollink.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import com.schoollink.invite.InviteCodeRepository;
import com.schoollink.notice.NoticeRepository;
import com.schoollink.school.SchoolClassRepository;
import com.schoollink.school.StudentRepository;
import com.schoollink.user.Role;
import com.schoollink.user.User;
import com.schoollink.user.UserRepository;

@Configuration
public class SeedConfig {
    @Bean
    @Order(1)
    CommandLineRunner seedUsers(UserRepository repo) {
        return args -> {
            // Ensure System Admin exists
            if (repo.findByUsername("sysadmin").isEmpty()) {
                repo.save(new User("sysadmin", "admin123", Role.SYSTEM_ADMIN));
                System.out.println("[seed] Created default SYSTEM_ADMIN: sysadmin");
            }

            // Ensure School Admin exists
            if (repo.findByUsername("school").isEmpty()) {
                repo.save(new User("school", "school123", Role.SCHOOL_ADMIN));
                System.out.println("[seed] Created default SCHOOL_ADMIN: school");
            }
        };
    }

    @Bean
    @Order(2)
    CommandLineRunner seedClassesAndStudents(SchoolClassRepository classRepo, StudentRepository studentRepo,
            UserRepository userRepo, InviteCodeRepository inviteRepo) {
        return args -> {
            // No default classes or students
        };
    }

    @Bean
    @Order(3)
    CommandLineRunner seedNotices(NoticeRepository noticeRepo, SchoolClassRepository classRepo) {
        return args -> {
            // No default notices
        };
    }
}
