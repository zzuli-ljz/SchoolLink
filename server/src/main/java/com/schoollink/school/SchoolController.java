package com.schoollink.school;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.schoollink.assignment.Assignment;
import com.schoollink.assignment.AssignmentRepository;
import com.schoollink.assignment.SubmissionRepository;
import com.schoollink.auth.JwtService;
import com.schoollink.invite.InviteCode;
import com.schoollink.invite.InviteCodeRepository;
import com.schoollink.leave.LeaveRepository;
import com.schoollink.notice.NoticeRepository;
import com.schoollink.notification.Notification;
import com.schoollink.notification.NotificationRepository;
import com.schoollink.user.Role;
import com.schoollink.user.User;
import com.schoollink.user.UserRepository;
import com.schoollink.user.UserService;

import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class SchoolController {
    private final SchoolClassRepository classRepo;
    private final StudentRepository studentRepo;
    private final JwtService jwtService;
    private final InviteCodeRepository inviteRepo;
    private final UserService userService;
    private final UserRepository userRepo;
    private final AssignmentRepository assignmentRepo;
    private final SubmissionRepository submissionRepo;
    private final NoticeRepository noticeRepo;
    private final LeaveRepository leaveRepo;
    private final ClassTeacherRepository classTeacherRepo;
    private final NotificationRepository notificationRepo;

    public SchoolController(SchoolClassRepository classRepo, StudentRepository studentRepo, JwtService jwtService,
            InviteCodeRepository inviteRepo, UserService userService, UserRepository userRepo,
            AssignmentRepository assignmentRepo, SubmissionRepository submissionRepo,
            NoticeRepository noticeRepo, LeaveRepository leaveRepo, ClassTeacherRepository classTeacherRepo,
            NotificationRepository notificationRepo) {
        this.classRepo = classRepo;
        this.studentRepo = studentRepo;
        this.jwtService = jwtService;
        this.inviteRepo = inviteRepo;
        this.userService = userService;
        this.userRepo = userRepo;
        this.assignmentRepo = assignmentRepo;
        this.submissionRepo = submissionRepo;
        this.noticeRepo = noticeRepo;
        this.leaveRepo = leaveRepo;
        this.classTeacherRepo = classTeacherRepo;
        this.notificationRepo = notificationRepo;
    }

    // 简单角色校验：仅允许 admin 角色进行写操作
    private boolean isAdmin(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            return false;
        try {
            String token = authHeader.substring(7);
            Claims claims = jwtService.parse(token);
            Object role = claims.get("role");
            return role != null && ("SYSTEM_ADMIN".equals(role) || "SCHOOL_ADMIN".equals(role));
        } catch (Exception e) {
            return false;
        }
    }

    private String roleFromAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            return null;
        try {
            String token = authHeader.substring(7);
            Claims claims = jwtService.parse(token);
            Object role = claims.get("role");
            return role == null ? null : role.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String subjectFromAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            return "unknown";
        try {
            String token = authHeader.substring(7);
            Claims claims = jwtService.parse(token);
            return claims.getSubject();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private boolean isTeacherOrAdmin(String role) {
        return role != null && (role.equals("TEACHER") || role.equals("SCHOOL_ADMIN") || role.equals("SYSTEM_ADMIN"));
    }

    @GetMapping("/my-class")
    public ResponseEntity<?> getMyClassInfo(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String username = subjectFromAuth(authorization);
        Optional<User> userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty())
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        User user = userOpt.get();
        if (user.getClassJoined() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "no_class_joined"));
        }
        return ResponseEntity.ok(user.getClassJoined());
    }

    @GetMapping("/parent/bound-student")
    public ResponseEntity<?> getBoundStudent(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String username = subjectFromAuth(authorization);
        Optional<User> userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty())
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        User user = userOpt.get();

        // 1. Check direct binding field
        if (user.getBoundStudentId() != null) {
            return ResponseEntity.ok(Map.of("studentId", user.getBoundStudentId()));
        }

        // 2. Check reverse relationship (Student.parentUser)
        // We need to query studentRepo for a student where parentUser.id = user.id
        // Since StudentRepository might not have this method exposed directly, we might
        // need to add it or iterate.
        // However, looking at deleteParent, we see: studentRepo.findByParentUser_Id(id)
        List<Student> bounds = studentRepo.findByParentUser_Id(user.getId());
        if (!bounds.isEmpty()) {
            // Pick the first one (assuming 1:1 or 1:N but we just need one for now)
            Student s = bounds.get(0);
            // Sync it back to user for performance next time
            user.setBoundStudentId(s.getId());
            userRepo.save(user);
            return ResponseEntity.ok(Map.of("studentId", s.getId()));
        }

        return ResponseEntity.ok(Map.of());
    }

    @PostMapping("/parent/bind-student")
    public ResponseEntity<?> bindStudent(@RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Long> req) {
        String username = subjectFromAuth(authorization);
        Optional<User> userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty())
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        User user = userOpt.get();

        Long studentId = req.get("studentId");
        if (studentId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "studentId required"));
        }

        if (!studentRepo.existsById(studentId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "student not found"));
        }

        user.setBoundStudentId(studentId);
        userRepo.save(user);
        return ResponseEntity.ok(Map.of("message", "bound successfully", "studentId", studentId));
    }

    // 班级列表与创建
    @GetMapping("/classes")
    public List<SchoolClass> listClasses(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String role = roleFromAuth(authorization);
        String username = subjectFromAuth(authorization);

        if ("TEACHER".equals(role)) {
            List<SchoolClass> owned = classRepo.findByOwnerUsername(username);
            
            // Find user ID
            Optional<User> u = userService.findByUsername(username);
            if (u.isPresent()) {
                List<ClassTeacher> cts = classTeacherRepo.findByTeacher_Id(u.get().getId());
                for (ClassTeacher ct : cts) {
                    if (owned.stream().noneMatch(c -> c.getId().equals(ct.getSchoolClass().getId()))) {
                        owned.add(ct.getSchoolClass());
                    }
                }
            }
            return owned;
        }
        // Admin or others see all
        return classRepo.findAll();
    }

    @GetMapping("/student/classmates")
    public ResponseEntity<?> listClassmates(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String username = subjectFromAuth(authorization);
        Optional<User> userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty())
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        User user = userOpt.get();

        // Auto-bind logic for STUDENT
        if (user.getBoundStudentId() == null && "STUDENT".equals(roleFromAuth(authorization))) {
            List<Student> linked = studentRepo.findByUsername(username);
            if (!linked.isEmpty()) {
                user.setBoundStudentId(linked.get(0).getId());
                userRepo.save(user);
            }
        }

        if (user.getBoundStudentId() == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "no_bound_student"));
        }

        Optional<Student> sOpt = studentRepo.findById(user.getBoundStudentId());
        if (sOpt.isEmpty())
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "student_not_found"));
        Student student = sOpt.get();

        if (student.getSchoolClass() == null) {
            return ResponseEntity.ok(List.of());
        }

        // Return classmates (excluding self? maybe, but usually includes self is fine)
        List<Student> classmates = studentRepo.findBySchoolClass_Id(student.getSchoolClass().getId());
        return ResponseEntity.ok(classmates);
    }

    @GetMapping("/student/contacts")
    public ResponseEntity<?> getStudentContacts(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String username = subjectFromAuth(authorization);
        String role = roleFromAuth(authorization);
        
        Optional<User> userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty())
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        User user = userOpt.get();

        Long studentId = null;

        if ("STUDENT".equals(role)) {
             // Ensure bound
            if (user.getBoundStudentId() == null) {
                List<Student> linked = studentRepo.findByUsername(username);
                if (!linked.isEmpty()) {
                    user.setBoundStudentId(linked.get(0).getId());
                    userRepo.save(user);
                }
            }
            studentId = user.getBoundStudentId();
        } else if ("PARENT".equals(role)) {
            studentId = user.getBoundStudentId();
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }

        if (studentId == null) {
            return ResponseEntity.ok(new java.util.HashMap<>());
        }

        Optional<Student> sOpt = studentRepo.findById(studentId);
        if (sOpt.isEmpty())
            return ResponseEntity.ok(new java.util.HashMap<>());

        Student student = sOpt.get();
        SchoolClass cls = student.getSchoolClass();
        
        List<Map<String, String>> teachersList = new java.util.ArrayList<>();
        
        if (cls != null) {
            // Head Teacher
            if (cls.getOwnerUsername() != null) {
                Map<String, String> ht = new java.util.HashMap<>();
                ht.put("username", cls.getOwnerUsername());
                ht.put("role", "班主任");
                ht.put("subject", "班级管理");
                teachersList.add(ht);
            }
            
            // Subject Teachers
            List<ClassTeacher> subjectTeachers = classTeacherRepo.findBySchoolClass_Id(cls.getId());
            for (ClassTeacher ct : subjectTeachers) {
                Map<String, String> st = new java.util.HashMap<>();
                st.put("username", ct.getTeacher().getUsername());
                st.put("role", "任课老师");
                st.put("subject", ct.getSubject());
                // Avoid duplicates if head teacher is also subject teacher (optional, but good for UI)
                // But usually we want to see them. Let's keep them, frontend can filter if needed.
                // Or better, distinct by username?
                // If head teacher is also a subject teacher, they will appear twice. 
                // Let's filter out if username exists.
                boolean exists = false;
                for(Map<String, String> existing : teachersList) {
                    if(existing.get("username").equals(st.get("username"))) {
                        // Merge subject info? 
                        // Let's just append subject to role or subject
                        existing.put("subject", existing.get("subject") + ", " + st.get("subject"));
                        exists = true;
                        break;
                    }
                }
                if(!exists) {
                    teachersList.add(st);
                }
            }
        }

        String parent = (student.getParentUser() != null) ? student.getParentUser().getUsername() : null;

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("teachers", teachersList);
        result.put("parent", parent);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/students")
    public ResponseEntity<?> listAllStudents(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String role = roleFromAuth(authorization);
        if (!isTeacherOrAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        
        if ("TEACHER".equals(role)) {
            String username = subjectFromAuth(authorization);
            List<SchoolClass> classes = classRepo.findByOwnerUsername(username);
            Optional<User> u = userService.findByUsername(username);
            if (u.isPresent()) {
                List<ClassTeacher> cts = classTeacherRepo.findByTeacher_Id(u.get().getId());
                for (ClassTeacher ct : cts) {
                    if (classes.stream().noneMatch(c -> c.getId().equals(ct.getSchoolClass().getId()))) {
                        classes.add(ct.getSchoolClass());
                    }
                }
            }
            if (classes.isEmpty()) {
                return ResponseEntity.ok(List.of());
            }
            List<Long> ids = classes.stream().map(SchoolClass::getId).toList();
            return ResponseEntity.ok(studentRepo.findBySchoolClass_IdIn(ids));
        }
        
        return ResponseEntity.ok(studentRepo.findAll());
    }

    @PostMapping("/classes")
    public ResponseEntity<?> createClass(@RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody SchoolClass req) {
        String role = roleFromAuth(authorization);
        if (!isTeacherOrAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        // 设置负责人：如果请求中指定了 ownerUsername 则使用，否则默认为创建者
        String creator = subjectFromAuth(authorization);
        if (req.getOwnerUsername() == null || req.getOwnerUsername().isBlank()) {
            req.setOwnerUsername(creator);
        }
        SchoolClass saved = classRepo.save(req);
        // 自动为该班级生成家长与学生邀请码
        String parentCode = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        InviteCode icParent = new InviteCode(parentCode, Role.PARENT, creator);
        icParent.setSchoolClass(saved);
        inviteRepo.save(icParent);
        String studentCode = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        InviteCode icStudent = new InviteCode(studentCode, Role.STUDENT, creator);
        icStudent.setSchoolClass(saved);
        inviteRepo.save(icStudent);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/classes/{id}")
    public ResponseEntity<?> getClass(@PathVariable("id") Long id) {
        Optional<SchoolClass> found = classRepo.findById(id);
        return found.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found")));
    }

    @PutMapping("/classes/{id}")
    public ResponseEntity<?> updateClass(@RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id,
            @Valid @RequestBody SchoolClass req) {
        return classRepo.findById(id).<ResponseEntity<Object>>map(existing -> {
            String role = roleFromAuth(authorization);
            if (!isTeacherOrAdmin(role)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
            }
            existing.setName(req.getName());
            existing.setGrade(req.getGrade());
            if (req.getOwnerUsername() != null && !req.getOwnerUsername().isBlank()) {
                existing.setOwnerUsername(req.getOwnerUsername());
            }
            SchoolClass saved = classRepo.save(existing);
            return ResponseEntity.ok((Object) saved);
        }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found")));
    }

    @DeleteMapping("/classes/{id}")
    @Transactional
    public ResponseEntity<?> deleteClass(@RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id) {
        Optional<SchoolClass> existingOpt = classRepo.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        }
        String role = roleFromAuth(authorization);
        if (!isTeacherOrAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        // 解除该班级下所有用户的关联，避免外键约束错误
        List<User> joinedUsers = userRepo.findByClassJoined_Id(id);
        if (joinedUsers != null && !joinedUsers.isEmpty()) {
            for (User u : joinedUsers) {
                u.setClassJoined(null);
                userRepo.save(u);
            }
            userRepo.flush(); // 强制提交更改
        }

        // 解除该班级下所有学生实体的关联
        List<Student> students = studentRepo.findBySchoolClass_Id(id);
        if (students != null && !students.isEmpty()) {
            for (Student s : students) {
                s.setSchoolClass(null);
                studentRepo.save(s);
            }
            studentRepo.flush(); // 强制提交更改
        }

        // 清理关联的作业及提交
        List<Assignment> assignments = assignmentRepo.findByClassId(id);
        for (Assignment a : assignments) {
            submissionRepo.deleteByAssignmentId(a.getId());
        }
        assignmentRepo.deleteByClassId(id);

        // 清理关联的通知
        noticeRepo.deleteByClassId(id);

        // 清理关联的请假申请
        leaveRepo.deleteByClassId(id);

        inviteRepo.deleteBySchoolClass_Id(id);
        classRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // 获取所有老师列表
    @GetMapping("/users/teachers")
    public ResponseEntity<?> listTeachers(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!isAdmin(authorization)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        return ResponseEntity.ok(userRepo.findByRole(Role.TEACHER));
    }

    // 获取学校统计数据
    @GetMapping("/school/stats")
    public ResponseEntity<?> getSchoolStats(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!isAdmin(authorization)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        long classCount = classRepo.count();
        long studentCount = userRepo.findByRole(Role.STUDENT).size();
        long teacherCount = userRepo.findByRole(Role.TEACHER).size();
        long noticeCount = noticeRepo.count();

        return ResponseEntity.ok(Map.of(
                "classCount", classCount,
                "studentCount", studentCount,
                "teacherCount", teacherCount,
                "noticeCount", noticeCount));
    }

    // 获取学校详细统计数据（报表）
    @GetMapping("/school/stats/detail")
    public ResponseEntity<?> getSchoolDetailedStats(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!isAdmin(authorization)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }

        List<SchoolClass> classes = classRepo.findAll();
        List<Map<String, Object>> classStats = new java.util.ArrayList<>();
        List<Map<String, Object>> teacherStats = new java.util.ArrayList<>();

        List<User> teachers = userRepo.findByRole(Role.TEACHER);
        Map<String, Integer> teacherActivity = new java.util.HashMap<>();
        for (User t : teachers) {
            teacherActivity.put(t.getUsername(), 0);
        }

        for (SchoolClass c : classes) {
            long studentCount = userRepo.findByClassJoined_Id(c.getId()).size();
            List<Assignment> assignments = assignmentRepo.findByClassId(c.getId());
            long assignmentCount = assignments.size();
            long submissionCount = 0;
            for (Assignment a : assignments) {
                submissionCount += submissionRepo.findByAssignmentId(a.getId()).size();
            }

            double submissionRate = 0.0;
            if (studentCount > 0 && assignmentCount > 0) {
                submissionRate = (double) submissionCount / (studentCount * assignmentCount);
            }

            long leaveCount = leaveRepo.findByClassId(c.getId()).size();

            List<String> warnings = new java.util.ArrayList<>();
            if (studentCount > 0 && assignmentCount > 0 && submissionRate < 0.5) {
                warnings.add("作业提交率低 (" + String.format("%.1f%%", submissionRate * 100) + ")");
            }
            if (leaveCount > studentCount * 2 && studentCount > 0) {
                warnings.add("请假频次高 (" + leaveCount + ")");
            }

            Map<String, Object> cStat = new java.util.HashMap<>();
            cStat.put("id", c.getId());
            cStat.put("name", c.getName());
            cStat.put("grade", c.getGrade());
            cStat.put("owner", c.getOwnerUsername());
            cStat.put("studentCount", studentCount);
            cStat.put("assignmentCount", assignmentCount);
            cStat.put("submissionRate", submissionRate);
            cStat.put("leaveCount", leaveCount);
            cStat.put("warnings", warnings);
            classStats.add(cStat);

            if (c.getOwnerUsername() != null && teacherActivity.containsKey(c.getOwnerUsername())) {
                teacherActivity.put(c.getOwnerUsername(),
                        teacherActivity.get(c.getOwnerUsername()) + (int) assignmentCount);
            }
        }

        for (User t : teachers) {
            Map<String, Object> tStat = new java.util.HashMap<>();
            tStat.put("id", t.getId());
            tStat.put("username", t.getUsername());
            tStat.put("activity", teacherActivity.getOrDefault(t.getUsername(), 0));
            teacherStats.add(tStat);
        }

        return ResponseEntity.ok(Map.of(
                "classStats", classStats,
                "teacherStats", teacherStats));
    }

    @PostMapping("/users/teachers")
    public ResponseEntity<?> createTeacher(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, String> body) {
        if (!isAdmin(authorization)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing_fields"));
        }

        if (userService.findByUsername(username).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "username_taken"));
        }

        User u = new User();
        u.setUsername(username);
        u.setPassword(password);
        u.setRole(Role.TEACHER);
        if (body.containsKey("phoneNumber")) {
            u.setPhoneNumber(body.get("phoneNumber"));
        }
        userService.save(u);
        return ResponseEntity.status(HttpStatus.CREATED).body(u);
    }

    @DeleteMapping("/users/teachers/{id}")
    public ResponseEntity<?> deleteTeacher(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id) {
        if (!isAdmin(authorization)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        Optional<User> uOpt = userService.findById(id);
        if (uOpt.isEmpty())
            return ResponseEntity.notFound().build();
        User u = uOpt.get();
        if (u.getRole() != Role.TEACHER) {
            return ResponseEntity.badRequest().body(Map.of("error", "not_teacher"));
        }
        userService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // 班级转让（将负责人变更为另一个老师）
    @PostMapping("/classes/{id}/transfer")
    public ResponseEntity<?> transferClass(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id,
            @RequestBody Map<String, String> body) {
        String role = roleFromAuth(authorization);
        if (role == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid_token"));
        }
        Optional<SchoolClass> opt = classRepo.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "class_not_found"));
        }
        if (!isTeacherOrAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        SchoolClass cls = opt.get();
        String toUsername = body.getOrDefault("toUsername", "").trim();
        if (toUsername.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "toUsername_required"));
        }
        Optional<User> targetOpt = userService.findByUsername(toUsername);
        if (targetOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "target_not_found"));
        }
        User target = targetOpt.get();
        if (target.getRole() != Role.TEACHER) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "target_not_teacher"));
        }
        cls.setOwnerUsername(toUsername);
        classRepo.save(cls);
        return ResponseEntity.ok(Map.of("id", cls.getId(), "ownerUsername", cls.getOwnerUsername()));
    }



    @GetMapping("/classes/{id}/students")
    public ResponseEntity<?> listStudentsByClass(@PathVariable("id") Long id) {
        if (!classRepo.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "class_not_found"));
        }
        List<Student> list = studentRepo.findBySchoolClass_Id(id);
        // 转换为 DTO 以包含绑定状态
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Student s : list) {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", s.getId());
            map.put("name", s.getName());
            map.put("studentNo", s.getStudentNo());
            map.put("createdAt", s.getCreatedAt());
            map.put("isBound", s.getParentUser() != null);
            if (s.getParentUser() != null) {
                map.put("parentUsername", s.getParentUser().getUsername());
                map.put("parentPhone", s.getParentUser().getPhoneNumber());
            }
            result.add(map);
        }
        return ResponseEntity.ok(result);
    }

    // 学生CRUD
    @PostMapping("/students")
    public ResponseEntity<?> createStudent(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody Student req) {
        if (!isAdmin(authorization)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        if (req.getSchoolClass() != null && req.getSchoolClass().getId() != null) {
            Optional<SchoolClass> cls = classRepo.findById(req.getSchoolClass().getId());
            if (cls.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "class_invalid"));
            }
            req.setSchoolClass(cls.get());
        }
        Student saved = studentRepo.save(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/students/{id}")
    public ResponseEntity<?> getStudent(@PathVariable("id") Long id) {
        Optional<Student> found = studentRepo.findById(id);
        return found.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found")));
    }

    @PutMapping("/students/{id}")
    public ResponseEntity<?> updateStudent(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id,
            @Valid @RequestBody Student req) {
        return studentRepo.findById(id).<ResponseEntity<Object>>map(existing -> {
            String role = roleFromAuth(authorization);
            if (!isTeacherOrAdmin(role)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
            }
            
            // Check ownership
            if (existing.getSchoolClass() != null) {
                String current = subjectFromAuth(authorization);
                String owner = existing.getSchoolClass().getOwnerUsername();
                if (!isAdmin(authorization) && (owner == null || !owner.equals(current))) {
                     return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "only_owner_can_edit_student"));
                }
            }
            
            existing.setName(req.getName());
            existing.setStudentNo(req.getStudentNo());
            if (req.getSchoolClass() != null && req.getSchoolClass().getId() != null) {
                Optional<SchoolClass> cls = classRepo.findById(req.getSchoolClass().getId());
                if (cls.isEmpty()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "class_invalid"));
                }
                existing.setSchoolClass(cls.get());
            }
            Student saved = studentRepo.save(existing);
            return ResponseEntity.ok((Object) saved);
        }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found")));
    }

    @DeleteMapping("/students/{id}")
    public ResponseEntity<?> deleteStudent(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id) {
        Optional<Student> existingOpt = studentRepo.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        }
        String role = roleFromAuth(authorization);
        if (!isTeacherOrAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        
        Student s = existingOpt.get();
        if (s.getSchoolClass() != null) {
            String current = subjectFromAuth(authorization);
            String owner = s.getSchoolClass().getOwnerUsername();
            if (!isAdmin(authorization) && (owner == null || !owner.equals(current))) {
                 return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "only_owner_can_delete_student"));
            }
        }
        
        studentRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // 家长信息：按班级列出、编辑、删除
    @GetMapping("/classes/{id}/parents")
    public ResponseEntity<?> listParentsByClass(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id) {
        String role = roleFromAuth(authorization);
        if (!isTeacherOrAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        if (!classRepo.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "class_not_found"));
        }
        // 密码已通过 @JsonIgnore 隐藏
        return ResponseEntity.ok(userService.findByClassAndRole(id, com.schoollink.user.Role.PARENT));
    }

    @PutMapping("/parents/{id}")
    public ResponseEntity<?> updateParent(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id,
            @RequestBody Map<String, String> body) {
        Optional<com.schoollink.user.User> userOpt = userService.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        }
        com.schoollink.user.User u = userOpt.get();
        if (u.getRole() != com.schoollink.user.Role.PARENT) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "not_parent"));
        }
        String role = roleFromAuth(authorization);
        if (!isTeacherOrAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        String newUsername = body.get("username");
        String newPassword = body.get("password");
        if (newUsername != null) {
            String v = newUsername.trim();
            if (!v.isEmpty()) {
                // 冲突检测
                Optional<com.schoollink.user.User> exists = userService.findByUsername(v);
                if (exists.isPresent() && !exists.get().getId().equals(u.getId())) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "username_taken"));
                }
                u.setUsername(v);
            }
        }
        if (newPassword != null) {
            String p = newPassword.trim();
            if (!p.isEmpty()) {
                u.setPassword(p);
            }
        }
        userService.save(u);
        return ResponseEntity.ok(Map.of("id", u.getId(), "username", u.getUsername()));
    }

    @DeleteMapping("/parents/{id}")
    public ResponseEntity<?> deleteParent(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id) {
        Optional<com.schoollink.user.User> userOpt = userService.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        }
        com.schoollink.user.User u = userOpt.get();
        if (u.getRole() != com.schoollink.user.Role.PARENT) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "not_parent"));
        }
        String role = roleFromAuth(authorization);
        if (!isTeacherOrAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }

        // Check ownership: Only Class Owner can delete parent
        List<Student> boundStudents = studentRepo.findByParentUser_Id(id);
        if (!isAdmin(authorization)) {
             String current = subjectFromAuth(authorization);
             boolean isOwner = false;
             for (Student s : boundStudents) {
                 if (s.getSchoolClass() != null) {
                     String owner = s.getSchoolClass().getOwnerUsername();
                     if (owner != null && owner.equals(current)) {
                         isOwner = true;
                         break;
                     }
                 }
             }
             // If parent has no students, maybe allow? Or deny?
             // If boundStudents is empty, only Admin should delete.
             if (!isOwner) {
                  return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "only_class_owner_can_delete_parent"));
             }
        }

        // 解绑关联的学生
        for (Student s : boundStudents) {
            s.setParentUser(null);
            studentRepo.save(s);
        }

        userService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // 通用：编辑/删除学生或家长账户（不允许操作老师/管理员）
    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUserAccount(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id,
            @RequestBody Map<String, String> body) {
        Optional<com.schoollink.user.User> userOpt = userService.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        }
        com.schoollink.user.User u = userOpt.get();
        if (u.getRole() != com.schoollink.user.Role.PARENT && u.getRole() != com.schoollink.user.Role.STUDENT) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "role_forbidden"));
        }
        String role = roleFromAuth(authorization);
        if (!isTeacherOrAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        String newUsername = body.get("username");
        String newPassword = body.get("password");
        if (newUsername != null) {
            String v = newUsername.trim();
            if (!v.isEmpty()) {
                Optional<com.schoollink.user.User> exists = userService.findByUsername(v);
                if (exists.isPresent() && !exists.get().getId().equals(u.getId())) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "username_taken"));
                }
                u.setUsername(v);
            }
        }
        if (newPassword != null) {
            String p = newPassword.trim();
            if (!p.isEmpty()) {
                u.setPassword(p);
            }
        }
        userService.save(u);
        return ResponseEntity.ok(Map.of("id", u.getId(), "username", u.getUsername(), "role", u.getRole().name()));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUserAccount(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id) {
        Optional<com.schoollink.user.User> userOpt = userService.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        }
        com.schoollink.user.User u = userOpt.get();
        if (u.getRole() != com.schoollink.user.Role.PARENT && u.getRole() != com.schoollink.user.Role.STUDENT) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "role_forbidden"));
        }
        String role = roleFromAuth(authorization);
        if (!isTeacherOrAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }

        if (u.getRole() == com.schoollink.user.Role.PARENT) {
            List<Student> boundStudents = studentRepo.findByParentUser_Id(id);
            for (Student s : boundStudents) {
                s.setParentUser(null);
                studentRepo.save(s);
            }
        }

        userService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // Subject Teacher Management
    @GetMapping("/classes/{classId}/subject-teachers")
    public ResponseEntity<?> listSubjectTeachers(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId) {
        String role = roleFromAuth(authorization);
        if (!isTeacherOrAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        return ResponseEntity.ok(classTeacherRepo.findBySchoolClass_Id(classId));
    }

    @PostMapping("/classes/{classId}/subject-teachers")
    public ResponseEntity<?> addSubjectTeacher(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @RequestBody Map<String, Object> body) {
        try {
            // Only owner or admin can add teachers
            Optional<SchoolClass> clsOpt = classRepo.findById(classId);
            if (clsOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            SchoolClass cls = clsOpt.get();

            String username = subjectFromAuth(authorization);
            boolean isOwner = cls.getOwnerUsername() != null && cls.getOwnerUsername().equals(username);
            if (!isOwner && !isAdmin(authorization)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
            }

            Long teacherId = null;
            if (body.containsKey("teacherId")) {
                teacherId = Long.valueOf(body.get("teacherId").toString());
            } else if (body.containsKey("teacherUsername")) {
                String tName = (String) body.get("teacherUsername");
                Optional<User> u = userService.findByUsername(tName); // Using userService or userRepo
                if (u.isPresent()) teacherId = u.get().getId();
            }
            
            if (teacherId == null) {
                 return ResponseEntity.badRequest().body(Map.of("error", "teacher_not_found"));
            }

            String subject = (String) body.get("subject");
            if (subject == null || subject.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "subject_required"));
            }
            
            Optional<User> teacherOpt = userRepo.findById(teacherId);
            if (teacherOpt.isEmpty() || !teacherOpt.get().getRole().equals(Role.TEACHER)) {
                 return ResponseEntity.badRequest().body(Map.of("error", "invalid_teacher"));
            }
            
            // Check if already exists
            List<ClassTeacher> existing = classTeacherRepo.findBySchoolClass_Id(classId);
            final Long finalTeacherId = teacherId;
            if (existing.stream().anyMatch(ct -> ct.getTeacher().getId().equals(finalTeacherId))) {
                 return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "teacher_already_added"));
            }

            ClassTeacher ct = new ClassTeacher(cls, teacherOpt.get(), subject);
            classTeacherRepo.save(ct);

            // Send notifications to students and parents
            List<Student> students = studentRepo.findBySchoolClass_Id(classId);
            String senderUsername = subjectFromAuth(authorization);
            String teacherName = teacherOpt.get().getUsername();
            
            for (Student s : students) {
                String title = "新任课老师加入";
                String content = "老师 " + teacherName + " 已作为 " + subject + " 老师加入您的班级。";
                
                // Notify Student
                if (s.getUsername() != null) {
                    Notification n = new Notification(s.getUsername(), senderUsername, title, content, "SYSTEM", null);
                    notificationRepo.save(n);
                }
                // Notify Parent
                 if (s.getParentUser() != null) {
                    Notification n = new Notification(s.getParentUser().getUsername(), senderUsername, title, content, "SYSTEM", null);
                    notificationRepo.save(n);
                }
            }
            return ResponseEntity.ok(Map.of("message", "success"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    @DeleteMapping("/classes/{classId}/subject-teachers/{teacherId}")
    public ResponseEntity<?> removeSubjectTeacher(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("teacherId") Long teacherId) {
        Optional<SchoolClass> clsOpt = classRepo.findById(classId);
        if (clsOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        SchoolClass cls = clsOpt.get();

        String username = subjectFromAuth(authorization);
        boolean isOwner = cls.getOwnerUsername() != null && cls.getOwnerUsername().equals(username);
        if (!isOwner && !isAdmin(authorization)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        
        List<ClassTeacher> existing = classTeacherRepo.findBySchoolClass_Id(classId);
        Optional<ClassTeacher> target = existing.stream().filter(ct -> ct.getTeacher().getId().equals(teacherId)).findFirst();
        
        if (target.isPresent()) {
            classTeacherRepo.delete(target.get());
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}