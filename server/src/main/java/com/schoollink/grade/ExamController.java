package com.schoollink.grade;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.schoollink.auth.JwtService;
import com.schoollink.notification.Notification;
import com.schoollink.notification.NotificationRepository;
import com.schoollink.school.Student;
import com.schoollink.school.StudentRepository;
import com.schoollink.user.User;
import com.schoollink.user.UserRepository;

import io.jsonwebtoken.Claims;

@RestController
@RequestMapping("/api/exams")
public class ExamController {
    private final ExamRepository examRepo;
    private final JwtService jwtService;
    private final NotificationRepository notificationRepo;
    private final UserRepository userRepo;
    private final StudentRepository studentRepo;

    public ExamController(ExamRepository examRepo, JwtService jwtService, 
            NotificationRepository notificationRepo, UserRepository userRepo, StudentRepository studentRepo) {
        this.examRepo = examRepo;
        this.jwtService = jwtService;
        this.notificationRepo = notificationRepo;
        this.userRepo = userRepo;
        this.studentRepo = studentRepo;
    }

    private String roleFromAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        try {
            String token = authHeader.substring(7);
            Claims claims = jwtService.parse(token);
            Object role = claims.get("role");
            return role == null ? null : role.toString();
        } catch (Exception e) {
            return null;
        }
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createExam(@RequestHeader("Authorization") String auth, @RequestBody Exam exam) {
        String role = roleFromAuth(auth);
        if (!"SCHOOL_ADMIN".equals(role) && !"SYSTEM_ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        
        if (exam.getName() == null || exam.getSemester() == null || exam.getGradeLevel() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing_fields"));
        }
        
        if (exam.getStartTime() == null) {
             return ResponseEntity.badRequest().body(Map.of("error", "missing_start_time"));
        }
        
        if (exam.getSubjects() == null || exam.getSubjects().isEmpty()) {
            // Default subjects if not provided
            exam.setSubjects(List.of("语文", "数学", "英语", "科学", "道法"));
        }

        exam.setCreatedAt(LocalDateTime.now());
        if (exam.getStatus() == null) exam.setStatus("PUBLISHED"); // Default to published for simplicity
        
        Exam saved = examRepo.save(exam);
        
        // Trigger notifications
        String sender = "system";
        try {
            sender = jwtService.parse(auth.substring(7)).getSubject();
        } catch (Exception e) {}

        String title = "新考试通知: " + saved.getName();
        String contentTeacher = "学校发布了新的考试安排：" + saved.getName() + " (" + saved.getSemester() + ")，请做好相关工作。";
        String contentStudent = "学校发布了新的考试安排：" + saved.getName() + " (" + saved.getSemester() + ")，请认真复习。";

        // 1. Notify Teachers
        List<User> teachers = userRepo.findByRole(com.schoollink.user.Role.TEACHER);
        for (User t : teachers) {
            notificationRepo.save(new Notification(t.getUsername(), sender, title, contentTeacher, "SYSTEM", null));
        }

        // 2. Notify Students & Parents (Filter by Grade)
        List<Student> students = studentRepo.findAll();
        for (Student s : students) {
            if (s.getSchoolClass() != null && s.getSchoolClass().getGrade() != null 
                    && s.getSchoolClass().getGrade().equals(saved.getGradeLevel())) {
                
                // Notify Student
                if (s.getUsername() != null) {
                    notificationRepo.save(new Notification(s.getUsername(), sender, title, contentStudent, "SYSTEM", null));
                }
                
                // Notify Parent
                if (s.getParentUser() != null) {
                    notificationRepo.save(new Notification(s.getParentUser().getUsername(), sender, title, 
                            "您的孩子(" + s.getName() + ")有新的考试安排：" + saved.getName(), "SYSTEM", null));
                }
            }
        }
        
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public ResponseEntity<?> listExams(@RequestHeader(value = "Authorization", required = false) String auth) {
        // Everyone can list exams (students need to select one, teachers need to select one)
        // Optionally filter by grade level for students if we had their profile handy, 
        // but listing all is fine for now.
        return ResponseEntity.ok(examRepo.findAll());
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<?> getExam(@PathVariable("id") Long id) {
        return examRepo.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
