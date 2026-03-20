package com.schoollink.assignment;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import com.schoollink.school.ClassTeacher;
import com.schoollink.school.ClassTeacherRepository;
import com.schoollink.school.SchoolClass;
import com.schoollink.school.SchoolClassRepository;
import com.schoollink.school.Student;
import com.schoollink.school.StudentRepository;
import com.schoollink.system.SystemConfig;
import com.schoollink.system.SystemConfigRepo;
import com.schoollink.user.User;
import com.schoollink.user.UserService;

import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class AssignmentController {
    private final AssignmentRepository assignmentRepo;
    private final SubmissionRepository submissionRepo;
    private final JwtService jwtService;
    private final UserService userService;
    private final StudentRepository studentRepo;
    private final NotificationRepository notificationRepo;
    private final SchoolClassRepository classRepo;
    private final SystemConfigRepo configRepo;
    private final ClassTeacherRepository classTeacherRepo;

    public AssignmentController(AssignmentRepository assignmentRepo, SubmissionRepository submissionRepo,
            JwtService jwtService, UserService userService, StudentRepository studentRepo,
            NotificationRepository notificationRepo, SchoolClassRepository classRepo, SystemConfigRepo configRepo,
            ClassTeacherRepository classTeacherRepo) {
        this.assignmentRepo = assignmentRepo;
        this.submissionRepo = submissionRepo;
        this.jwtService = jwtService;
        this.userService = userService;
        this.studentRepo = studentRepo;
        this.notificationRepo = notificationRepo;
        this.classRepo = classRepo;
        this.configRepo = configRepo;
        this.classTeacherRepo = classTeacherRepo;
    }

    private boolean isModuleEnabled() {
        boolean enabled = configRepo.findById(1L).map(SystemConfig::isModuleAssignmentEnabled).orElse(true);
        System.out.println("DEBUG: isAssignmentModuleEnabled check: " + enabled);
        return enabled;
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
            System.out.println("Auth Error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private String usernameFromAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            return null;
        try {
            String token = authHeader.substring(7);
            Claims claims = jwtService.parse(token);
            return claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isTeacherOrAdmin(String role) {
        return role != null && (role.equals("TEACHER") || role.equals("SCHOOL_ADMIN") || role.equals("SYSTEM_ADMIN"));
    }

    private boolean isStudent(String role) {
        return "STUDENT".equals(role);
    }

    private boolean isParent(String role) {
        return "PARENT".equals(role);
    }

    @PostMapping("/classes/{classId}/assignments")
    public ResponseEntity<?> createAssignment(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @Valid @RequestBody Assignment req) {
        if (!isModuleEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "module_disabled"));
        }
        String role = roleFromAuth(authorization);
        if (!isTeacherOrAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        
        String username = usernameFromAuth(authorization);
        Optional<User> userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Verify permission: Owner or Subject Teacher
        Optional<SchoolClass> clsOpt = classRepo.findById(classId);
        if (clsOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        SchoolClass cls = clsOpt.get();
        
        boolean isOwner = cls.getOwnerUsername() != null && cls.getOwnerUsername().equals(username);
        boolean isSubjectTeacher = false;
        
        // Check subject teacher
        if (!isOwner) {
            List<ClassTeacher> cts = classTeacherRepo.findBySchoolClass_Id(classId);
            isSubjectTeacher = cts.stream().anyMatch(ct -> ct.getTeacher().getId().equals(userOpt.get().getId()));
        }

        if (!isOwner && !isSubjectTeacher && !"SYSTEM_ADMIN".equals(role) && !"SCHOOL_ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden_not_in_class"));
        }
        
        req.setClassId(classId);
        req.setTeacher(userOpt.get());
        Assignment saved = assignmentRepo.save(req);

        // Auto-create notifications for students in the class
        String senderName = username;
        List<Student> students = studentRepo.findBySchoolClass_Id(classId);
        for (Student s : students) {
            // Only send notification if student is linked to a user (has username)
            if (s.getUsername() != null) {
                Notification n = new Notification(
                        s.getUsername(),
                        senderName,
                        "新作业: " + saved.getTitle(),
                        "请点击查看作业详情并按时提交。",
                        "ASSIGNMENT",
                        saved.getId());
                notificationRepo.save(n);
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/assignments/published")
    public ResponseEntity<?> listPublishedAssignments(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String role = roleFromAuth(authorization);
        if (!isTeacherOrAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        String username = usernameFromAuth(authorization);
        
        // 1. Assignments published by me
        List<Assignment> myAssignments = assignmentRepo.findByTeacher_Username(username);
        
        // 2. Assignments in classes I own
        List<SchoolClass> ownedClasses = classRepo.findByOwnerUsername(username);
        List<Assignment> classAssignments = new java.util.ArrayList<>();
        for (SchoolClass cls : ownedClasses) {
            classAssignments.addAll(assignmentRepo.findByClassId(cls.getId()));
        }

        // Merge and Distinct
        java.util.Set<Assignment> resultSet = new java.util.HashSet<>();
        resultSet.addAll(myAssignments);
        resultSet.addAll(classAssignments);
        
        List<Assignment> result = new java.util.ArrayList<>(resultSet);
        result.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt())); // Newest first
        return ResponseEntity.ok(result);
    }

    @GetMapping("/classes/{classId}/assignments")
    public ResponseEntity<?> listAssignments(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId) {
        List<Assignment> all = assignmentRepo.findByClassId(classId);
        String role = roleFromAuth(authorization);
        
        if ("TEACHER".equals(role)) {
             String username = usernameFromAuth(authorization);
             Optional<SchoolClass> clsOpt = classRepo.findById(classId);
             if (clsOpt.isPresent()) {
                 String owner = clsOpt.get().getOwnerUsername();
                 boolean isOwner = owner != null && owner.equals(username);
                 
                 List<Assignment> filtered = all.stream().filter(a -> {
                     if (isOwner) return true;
                     if (a.getTeacher() != null && a.getTeacher().getUsername().equals(username)) return true;
                     return false;
                 }).collect(java.util.stream.Collectors.toList());
                 return ResponseEntity.ok(filtered);
             }
        }
        return ResponseEntity.ok(all);
    }

    // New Endpoint: List Student Assignments with Status
    @GetMapping("/student/assignments")
    public ResponseEntity<?> listMyAssignments(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @org.springframework.web.bind.annotation.RequestParam(value = "studentId", required = false) Long studentId) {
        if (!isModuleEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "module_disabled"));
        }
        try {
            String role = roleFromAuth(authorization);
            String username = usernameFromAuth(authorization);
            System.out.println("listMyAssignments: role=" + role + ", username=" + username);

            Optional<User> userOpt = userService.findByUsername(username);
            if (userOpt.isEmpty())
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "user_not_found"));
            User user = userOpt.get();

            Long targetStudentId = null;

            if ("PARENT".equals(role)) {
                if (studentId == null) {
                    if (user.getBoundStudentId() != null) {
                        targetStudentId = user.getBoundStudentId();
                    } else {
                        return ResponseEntity.badRequest().body(Map.of("error", "studentId required"));
                    }
                } else {
                    targetStudentId = studentId;
                }

                if (user.getBoundStudentId() == null || !user.getBoundStudentId().equals(targetStudentId)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "not_bound_student"));
                }
            } else if ("STUDENT".equals(role)) {
                if (user.getBoundStudentId() == null) {
                    // Try to find student by username as fallback
                    List<Student> linked = studentRepo.findByUsername(username);
                    if (!linked.isEmpty()) {
                        targetStudentId = linked.get(0).getId();
                        // Auto-bind for future
                        user.setBoundStudentId(targetStudentId);
                        // We can't easily save user here without repo, but it's fine
                    } else {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(Map.of("error", "student_not_bound_to_user"));
                    }
                } else {
                    targetStudentId = user.getBoundStudentId();
                }
            } else {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden_role"));
            }

            if (targetStudentId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "studentId missing"));
            }

            Optional<Student> sOpt = studentRepo.findById(targetStudentId);
            if (sOpt.isEmpty())
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "student_not_found"));
            Student student = sOpt.get();

            if (student.getSchoolClass() == null)
                return ResponseEntity.ok(List.of());

            List<Assignment> assignments = assignmentRepo.findByClassId(student.getSchoolClass().getId());
            List<Submission> submissions = submissionRepo.findByStudentId(student.getId());

            List<Map<String, Object>> result = new java.util.ArrayList<>();
            LocalDate now = LocalDate.now();
            String publisher = student.getSchoolClass().getOwnerUsername();
            if (publisher == null)
                publisher = "老师";

            for (Assignment a : assignments) {
                Submission sub = submissions.stream().filter(s -> s.getAssignmentId().equals(a.getId())).findFirst()
                        .orElse(null);
                String status = "UNFINISHED";

                // Check extended deadline if returned
                LocalDate deadline = a.getDueDate();
                if (sub != null && "RETURNED".equals(sub.getStatus()) && sub.getExtendedDueDate() != null) {
                    deadline = sub.getExtendedDueDate();
                }

                if (sub != null) {
                    if ("GRADED".equals(sub.getStatus()) || sub.getScore() != null) {
                        status = "COMPLETED";
                    } else if ("RETURNED".equals(sub.getStatus())) {
                        status = "RETURNED";
                        if (deadline != null && now.isAfter(deadline)) {
                            status = "EXPIRED";
                        }
                    } else {
                        status = "PENDING_GRADING";
                    }
                } else if (deadline != null && now.isAfter(deadline)) {
                    status = "EXPIRED";
                }

                Map<String, Object> item = new java.util.HashMap<>();
                item.put("assignment", a);
                item.put("status", status);
                item.put("submission", sub);
                item.put("publisher", publisher);
                result.add(item);
            }
            // Sort by created at desc
            result.sort((x, y) -> {
                Assignment a1 = (Assignment) x.get("assignment");
                Assignment a2 = (Assignment) y.get("assignment");
                return a2.getCreatedAt().compareTo(a1.getCreatedAt());
            });

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/assignments/{id}/submissions")
    public ResponseEntity<?> submitAssignment(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id,
            @Valid @RequestBody Submission req) {
        String role = roleFromAuth(authorization);
        String username = usernameFromAuth(authorization);
        Optional<User> userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty())
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "user_not_found"));
        User user = userOpt.get();
        if (user.getClassJoined() == null)
            return ResponseEntity.badRequest().body(Map.of("error", "no_class_joined"));

        Student student = null;

        if (isStudent(role)) {
            List<Student> linkedStudents = studentRepo.findByUsername(username);
            if (linkedStudents.isEmpty())
                return ResponseEntity.badRequest().body(Map.of("error", "student_record_not_found"));
            student = linkedStudents.get(0);
        } else if (isParent(role)) {
            if (req.getStudentId() == null)
                return ResponseEntity.badRequest().body(Map.of("error", "student_id_required"));
            Optional<Student> sOpt = studentRepo.findById(req.getStudentId());
            if (sOpt.isEmpty())
                return ResponseEntity.badRequest().body(Map.of("error", "student_not_found"));
            student = sOpt.get();
            // Verify
            if (!student.getSchoolClass().getId().equals(user.getClassJoined().getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "student_not_in_your_class"));
            }
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }

        // Check assignment expiration
        Optional<Assignment> assignmentOpt = assignmentRepo.findById(id);
        if (assignmentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "assignment_not_found"));
        }
        Assignment assignment = assignmentOpt.get();

        // Check if already submitted
        Submission existing = submissionRepo.findByStudentIdAndAssignmentId(student.getId(), id);
        
        // Calculate deadline (considering extensions)
        java.time.LocalDate deadline = assignment.getDueDate();
        if (existing != null && "RETURNED".equals(existing.getStatus()) && existing.getExtendedDueDate() != null) {
            deadline = existing.getExtendedDueDate();
        }

        if (deadline != null && java.time.LocalDate.now().isAfter(deadline)) {
            return ResponseEntity.badRequest().body(Map.of("error", "assignment_expired"));
        }

        if (existing != null) {
            // Update existing
            existing.setContent(req.getContent());
            existing.setSubmittedAt(java.time.OffsetDateTime.now());
            // If it was returned, reset to PENDING after re-submission
            existing.setStatus("PENDING");
            submissionRepo.save(existing);
            return ResponseEntity.ok(existing);
        }

        req.setAssignmentId(id);
        req.setStudentId(student.getId());
        req.setStatus("PENDING");
        Submission saved = submissionRepo.save(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/assignments/{id}")
    public ResponseEntity<?> getAssignment(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id) {
        Optional<Assignment> assignment = assignmentRepo.findById(id);
        if (assignment.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "assignment_not_found"));
        }
        return ResponseEntity.ok(assignment.get());
    }

    @GetMapping("/assignments/{id}/submissions")
    public ResponseEntity<?> listSubmissions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id) {
        String role = roleFromAuth(authorization);
        if (!isTeacherOrAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        
        Optional<Assignment> assignmentOpt = assignmentRepo.findById(id);
        if (assignmentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "assignment_not_found"));
        }
        Assignment assignment = assignmentOpt.get();
        
        // Get all students in class
        List<Student> students = studentRepo.findBySchoolClass_Id(assignment.getClassId());
        
        // Get all submissions
        List<Submission> submissions = submissionRepo.findByAssignmentId(id);
        
        // Build result list
        List<Map<String, Object>> studentStatusList = new java.util.ArrayList<>();
        
        for (Student s : students) {
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("studentId", s.getId());
            item.put("studentName", s.getName());
            item.put("studentNo", s.getStudentNo());
            
            Optional<Submission> sub = submissions.stream()
                .filter(submission -> submission.getStudentId().equals(s.getId()))
                .findFirst();
                
            if (sub.isPresent()) {
                item.put("hasSubmitted", true);
                item.put("submission", sub.get());
                item.put("status", sub.get().getStatus());
            } else {
                item.put("hasSubmitted", false);
                item.put("status", "MISSING");
            }
            studentStatusList.add(item);
        }
        
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("assignment", assignment);
        result.put("students", studentStatusList);
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/assignments/{id}/stats")
    public ResponseEntity<?> getAssignmentStats(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id) {
        String role = roleFromAuth(authorization);
        if (!isTeacherOrAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }

        Optional<Assignment> assignmentOpt = assignmentRepo.findById(id);
        if (assignmentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "assignment_not_found"));
        }
        Assignment assignment = assignmentOpt.get();

        // Count students in class
        List<Student> students = studentRepo.findBySchoolClass_Id(assignment.getClassId());
        int totalStudents = students.size();

        // Count submissions
        List<Submission> submissions = submissionRepo.findByAssignmentId(id);
        int submittedCount = submissions.size();
        
        // Count graded
        long gradedCount = submissions.stream()
                .filter(s -> "GRADED".equals(s.getStatus()) || s.getScore() != null)
                .count();
        
        // Count returned
        long returnedCount = submissions.stream()
                .filter(s -> "RETURNED".equals(s.getStatus()))
                .count();

        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("totalStudents", totalStudents);
        stats.put("submittedCount", submittedCount);
        stats.put("gradedCount", gradedCount);
        stats.put("returnedCount", returnedCount);
        
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/submissions/{id}/grade")
    public ResponseEntity<?> gradeSubmission(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id,
            @RequestBody Map<String, Object> req) {
        String role = roleFromAuth(authorization);
        if (!isTeacherOrAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }

        Optional<Submission> subOpt = submissionRepo.findById(id);
        if (subOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "submission_not_found"));
        }
        Submission sub = subOpt.get();

        // Check permission: only publisher can grade
        String username = usernameFromAuth(authorization);
        Optional<Assignment> assignOpt = assignmentRepo.findById(sub.getAssignmentId());
        if (assignOpt.isPresent()) {
            Assignment assign = assignOpt.get();
            if (assign.getTeacher() != null && !assign.getTeacher().getUsername().equals(username)) {
                 return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "only_publisher_can_grade"));
            }
        }

        if (req.containsKey("score")) {
            Object s = req.get("score");
            if (s instanceof Integer) {
                sub.setScore((Integer) s);
            } else if (s instanceof String) {
                try {
                    String sStr = (String) s;
                    if (!sStr.isBlank()) {
                        sub.setScore(Integer.parseInt(sStr));
                    }
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        if (req.containsKey("feedback")) {
            sub.setFeedback((String) req.get("feedback"));
        }
        sub.setStatus("GRADED");
        
        submissionRepo.save(sub);
        
        // Notify student
        Optional<Student> studentOpt = studentRepo.findById(sub.getStudentId());
        if (studentOpt.isPresent() && studentOpt.get().getUsername() != null) {
             String senderName = usernameFromAuth(authorization);
             Notification n = new Notification(
                 studentOpt.get().getUsername(),
                 senderName,
                 "作业已批改",
                 "您的作业已被批改，得分: " + sub.getScore(),
                 "ASSIGNMENT_GRADED",
                 sub.getAssignmentId()
             );
             notificationRepo.save(n);
        }

        return ResponseEntity.ok(sub);
    }

    @PostMapping("/submissions/{id}/return")
    public ResponseEntity<?> returnSubmission(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id,
            @RequestBody Map<String, Object> req) {
        String role = roleFromAuth(authorization);
        if (!isTeacherOrAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }

        Optional<Submission> subOpt = submissionRepo.findById(id);
        if (subOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "submission_not_found"));
        }
        Submission sub = subOpt.get();

        // Check permission: only publisher can return
        String username = usernameFromAuth(authorization);
        Optional<Assignment> assignOpt = assignmentRepo.findById(sub.getAssignmentId());
        if (assignOpt.isPresent()) {
            Assignment assign = assignOpt.get();
            if (assign.getTeacher() != null && !assign.getTeacher().getUsername().equals(username)) {
                 return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "only_publisher_can_return"));
            }
        }

        if (req.containsKey("extendedDueDate")) {
            try {
                String dateStr = (String) req.get("extendedDueDate");
                if (dateStr != null && !dateStr.isBlank()) {
                    sub.setExtendedDueDate(java.time.LocalDate.parse(dateStr));
                }
            } catch (Exception e) {
                 return ResponseEntity.badRequest().body(Map.of("error", "invalid_date_format"));
            }
        }
        if (req.containsKey("feedback")) {
            sub.setFeedback((String) req.get("feedback"));
        }
        sub.setStatus("RETURNED");
        
        submissionRepo.save(sub);

        // Notify student
        Optional<Student> studentOpt = studentRepo.findById(sub.getStudentId());
        if (studentOpt.isPresent() && studentOpt.get().getUsername() != null) {
             String senderName = usernameFromAuth(authorization);
             Notification n = new Notification(
                 studentOpt.get().getUsername(),
                 senderName,
                 "作业被退回",
                 "您的作业需重做/完善。截止日期已延长。",
                 "ASSIGNMENT_RETURNED",
                 sub.getAssignmentId()
             );
             notificationRepo.save(n);
        }

        return ResponseEntity.ok(sub);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/assignments/{id}")
    public ResponseEntity<?> deleteAssignment(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id) {
        String role = roleFromAuth(authorization);
        if (!isTeacherOrAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        
        Optional<Assignment> assignmentOpt = assignmentRepo.findById(id);
        if (assignmentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "assignment_not_found"));
        }
        Assignment assignment = assignmentOpt.get();
        
        // Verify ownership: Only publisher or Admin can delete
        String username = usernameFromAuth(authorization);
        boolean isPublisher = assignment.getTeacher() != null && assignment.getTeacher().getUsername().equals(username);
        boolean isAdmin = role.equals("SYSTEM_ADMIN") || role.equals("SCHOOL_ADMIN");
        
        if (!isPublisher && !isAdmin) {
             return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "only_publisher_can_delete"));
        }
        
        // Delete all submissions first (Manual cascade if needed, though JPA usually handles if configured. 
        // Here we do it manually to be safe and ensure clean up)
        List<Submission> submissions = submissionRepo.findByAssignmentId(id);
        submissionRepo.deleteAll(submissions);
        
        // Delete notifications related to this assignment
        // (Optional: Implement if Notification has assignmentId reference to clean up)
        
        assignmentRepo.delete(assignment);
        
        return ResponseEntity.ok(Map.of("message", "deleted_successfully"));
    }

    @org.springframework.web.bind.annotation.PutMapping("/assignments/{id}")
    public ResponseEntity<?> updateAssignment(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id,
            @RequestBody Assignment req) {
        String role = roleFromAuth(authorization);
        if (!isTeacherOrAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        
        Optional<Assignment> assignmentOpt = assignmentRepo.findById(id);
        if (assignmentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "assignment_not_found"));
        }
        Assignment assignment = assignmentOpt.get();
        
        // Verify ownership
        String username = usernameFromAuth(authorization);
        boolean isPublisher = assignment.getTeacher() != null && assignment.getTeacher().getUsername().equals(username);
        boolean isAdmin = role.equals("SYSTEM_ADMIN") || role.equals("SCHOOL_ADMIN");
        
        if (!isPublisher && !isAdmin) {
             return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "only_publisher_can_edit"));
        }
        
        if (req.getTitle() != null) assignment.setTitle(req.getTitle());
        if (req.getContent() != null) assignment.setContent(req.getContent());
        if (req.getDueDate() != null) assignment.setDueDate(req.getDueDate());
        
        Assignment updated = assignmentRepo.save(assignment);
        return ResponseEntity.ok(updated);
    }
}
