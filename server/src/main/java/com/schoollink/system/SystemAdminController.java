package com.schoollink.system;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.schoollink.auth.JwtService;
import com.schoollink.user.Role;
import com.schoollink.user.User;
import com.schoollink.user.UserService;
import com.schoollink.school.StudentRepository;
import com.schoollink.school.ClassTeacherRepository;
import com.schoollink.assignment.AssignmentRepository;
import com.schoollink.school.Student;
import com.schoollink.school.ClassTeacher;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.transaction.annotation.Transactional;

import io.jsonwebtoken.Claims;

@RestController
@RequestMapping("/api/admin")
public class SystemAdminController {

    @Autowired
    private SystemConfigRepo configRepo;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private StudentRepository studentRepo;

    @Autowired
    private ClassTeacherRepository classTeacherRepo;

    @Autowired
    private AssignmentRepository assignmentRepo;

    // Helper: Verify System Admin
    private boolean isSystemAdmin(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer "))
            return false;
        String token = authorization.substring(7);
        try {
            Claims claims = jwtService.parse(token);
            String roleName = (String) claims.get("role");
            return "SYSTEM_ADMIN".equals(roleName);
        } catch (Exception e) {
            return false;
        }
    }

    // 1. System Config
    @GetMapping("/config")
    public ResponseEntity<?> getConfig(@RequestHeader(value = "Authorization", required = false) String auth) {
        // Allow any authenticated user to read config to adjust UI (e.g. hide modules)
        if (auth == null || !auth.startsWith("Bearer "))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(getOrCreateConfig());
    }

    @PutMapping("/config")
    public ResponseEntity<?> updateConfig(@RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody SystemConfig newConfig) {
        if (!isSystemAdmin(auth))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        SystemConfig current = getOrCreateConfig();
        current.setSystemName(newConfig.getSystemName());
        current.setLogoUrl(newConfig.getLogoUrl());
        current.setMaxAttachmentSizeMb(newConfig.getMaxAttachmentSizeMb());
        current.setModuleNoticeEnabled(newConfig.isModuleNoticeEnabled());
        current.setModuleAssignmentEnabled(newConfig.isModuleAssignmentEnabled());
        current.setModuleAttendanceEnabled(newConfig.isModuleAttendanceEnabled());

        configRepo.save(current);
        return ResponseEntity.ok(current);
    }

    @Autowired
    private com.schoollink.user.UserRepository userRepository;

    private SystemConfig getOrCreateConfig() {
        return configRepo.findById(1L).orElseGet(() -> {
            SystemConfig c = new SystemConfig();
            c.setId(1L);
            return configRepo.save(c);
        });
    }

    // 2. User Management
    @GetMapping("/users")
    public ResponseEntity<?> listUsers(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (!isSystemAdmin(auth))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        // Return all users. In production, use pagination.
        // Hide password hash? JsonIgnore is on password field in User entity.
        // But we might want to return extra info.
        // UserService.findAll() is not exposed directly, let's assume we can access
        // repo via service or inject repo.
        // Service doesn't have findAll, so let's use userService.userRepository if
        // possible, or add findAll to service.
        // Since I can't easily change service signature without checking, I'll inject
        // repo here too or just use what I have.
        // I'll add findAll to UserService later if needed, but for now I will inject
        // UserRepo here or assume I can add it to service.
        // Actually I can just inject UserRepository here.
        return ResponseEntity.ok(userRepository.findAll());
    }

    @PutMapping("/users/{id}/status")
    public ResponseEntity<?> toggleUserStatus(@RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable("id") Long id,
            @RequestBody Map<String, Boolean> body) {
        try {
            // Detailed Auth Check
            if (auth == null || !auth.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No valid Authorization header"));
            }
            String token = auth.substring(7);
            try {
                Claims claims = jwtService.parse(token);
                String roleName = (String) claims.get("role");
                if (!"SYSTEM_ADMIN".equals(roleName)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "权限不足: 需要 SYSTEM_ADMIN, 当前是 " + roleName));
                }
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Token 无效: " + e.getMessage()));
            }

            Optional<User> uOpt = userService.findById(id);
            if (uOpt.isEmpty())
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "用户不存在 (ID: " + id + ")"));

            User u = uOpt.get();
            if (body.containsKey("enabled")) {
                Boolean val = body.get("enabled");
                if (val == null) {
                    return ResponseEntity.badRequest().body(Map.of("error", "enabled 字段不能为空"));
                }
                u.setEnabled(val);
                User saved = userService.save(u);
                return ResponseEntity.ok(saved);
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "请求体中缺少 enabled 字段"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "服务器内部错误: " + e.getMessage()));
        }
    }

    @PutMapping("/users/{id}/reset-password")
    public ResponseEntity<?> resetPassword(@RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable("id") Long id,
            @RequestBody Map<String, String> body) {
        try {
            // Detailed Auth Check
            if (auth == null || !auth.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No valid Authorization header"));
            }
            String token = auth.substring(7);
            try {
                Claims claims = jwtService.parse(token);
                String roleName = (String) claims.get("role");
                if (!"SYSTEM_ADMIN".equals(roleName)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "权限不足: 需要 SYSTEM_ADMIN, 当前是 " + roleName));
                }
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Token 无效: " + e.getMessage()));
            }

            String newPass = body.get("password");
            if (newPass == null || newPass.isBlank())
                return ResponseEntity.badRequest().body(Map.of("error", "密码不能为空"));

            Optional<User> uOpt = userService.findById(id);
            if (uOpt.isEmpty())
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "用户不存在 (ID: " + id + ")"));

            User u = uOpt.get();
            u.setPassword(newPass);
            userService.save(u);
            return ResponseEntity.ok(Map.of("message", "密码重置成功"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "服务器内部错误: " + e.getMessage()));
        }
    }

    @DeleteMapping("/users/{id}")
    @Transactional
    public ResponseEntity<?> deleteUser(@RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable("id") Long id) {
        if (!isSystemAdmin(auth))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        
        Optional<User> uOpt = userService.findById(id);
        if (uOpt.isEmpty())
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "用户不存在"));
        
        User u = uOpt.get();
        try {
            // Handle associations based on role
            if (u.getRole() == Role.PARENT) {
                // Unbind from students
                List<Student> students = studentRepo.findByParentUser_Id(id);
                for (Student s : students) {
                    s.setParentUser(null);
                    studentRepo.save(s);
                }
            } else if (u.getRole() == Role.TEACHER) {
                // Remove from class teachers
                List<ClassTeacher> cts = classTeacherRepo.findByTeacher_Id(id);
                classTeacherRepo.deleteAll(cts);
                
                // Remove assignments
                assignmentRepo.deleteByTeacher_Id(id);
            }
            
            // Finally delete the user
            userRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "用户已删除"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "删除失败: " + e.getMessage()));
        }
    }

    private Role parseRole(String val) {
        if (val == null)
            return null;
        String s = val.trim().toUpperCase();
        switch (val.trim()) {
            case "系统管理员": return Role.SYSTEM_ADMIN;
            case "学校管理员": return Role.SCHOOL_ADMIN;
            case "老师": return Role.TEACHER;
            case "家长": return Role.PARENT;
            case "学生": return Role.STUDENT;
            default: break;
        }
        try {
            return Role.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @PostMapping("/users/import")
    public ResponseEntity<?> importUsers(@RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody List<Map<String, String>> users) {
        // Detailed Auth Check
        if (auth == null || !auth.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No valid Authorization header"));
        }
        String token = auth.substring(7);
        try {
            Claims claims = jwtService.parse(token);
            String roleName = (String) claims.get("role");
            if (!"SYSTEM_ADMIN".equals(roleName)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "权限不足: 需要 SYSTEM_ADMIN, 当前是 " + roleName));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Token 无效: " + e.getMessage()));
        }

        int count = 0;
        List<String> details = new java.util.ArrayList<>();

        for (Map<String, String> userData : users) {
            String username = userData.get("username");
            String password = userData.get("password");
            String roleStr = userData.get("role");

            if (username == null || username.isBlank()) {
                details.add("Skip: Username missing");
                continue;
            }
            if (userService.findByUsername(username).isPresent()) {
                details.add("Skip: User '" + username + "' already exists");
                continue;
            }

            try {
                Role role = parseRole(roleStr);
                if (role == null) {
                    details.add("Skip: Invalid role '" + roleStr + "' for user '" + username + "'");
                    continue;
                }
                
                User u = new User(username, password, role);
                if (userData.containsKey("phoneNumber"))
                    u.setPhoneNumber(userData.get("phoneNumber"));
                userService.save(u);
                count++;
                details.add("Success: User '" + username + "' created");
            } catch (Exception e) {
                details.add("Error: Failed to create user '" + username + "': " + e.getMessage());
            }
        }
        return ResponseEntity.ok(Map.of("imported", count, "details", details));
    }
}
