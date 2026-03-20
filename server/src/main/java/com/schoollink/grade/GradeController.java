package com.schoollink.grade;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.schoollink.auth.JwtService;
import com.schoollink.notification.Notification;
import com.schoollink.notification.NotificationRepository;
import com.schoollink.school.ClassTeacher;
import com.schoollink.school.ClassTeacherRepository;
import com.schoollink.school.SchoolClass;
import com.schoollink.school.SchoolClassRepository;
import com.schoollink.school.Student;
import com.schoollink.school.StudentRepository;
import com.schoollink.user.Role;
import com.schoollink.user.User;
import com.schoollink.user.UserRepository;

import io.jsonwebtoken.Claims;

@RestController
@RequestMapping("/api/grades")
public class GradeController {
    private final ExamRepository examRepo;
    private final ExamResultRepository resultRepo;
    private final UserRepository userRepo;
    private final SchoolClassRepository classRepo;
    private final ClassTeacherRepository classTeacherRepo;
    private final JwtService jwtService;
    private final NotificationRepository notificationRepo;
    private final StudentRepository studentRepo;

    public GradeController(ExamRepository examRepo, ExamResultRepository resultRepo, UserRepository userRepo,
                           SchoolClassRepository classRepo, ClassTeacherRepository classTeacherRepo, JwtService jwtService,
                           NotificationRepository notificationRepo, StudentRepository studentRepo) {
        this.examRepo = examRepo;
        this.resultRepo = resultRepo;
        this.userRepo = userRepo;
        this.classRepo = classRepo;
        this.classTeacherRepo = classTeacherRepo;
        this.jwtService = jwtService;
        this.notificationRepo = notificationRepo;
        this.studentRepo = studentRepo;
    }

    // Helper: Detect encoding (UTF-8 or GBK)
    private java.nio.charset.Charset detectEncoding(byte[] bytes) {
        // 1. Check BOM
        if (bytes.length >= 3 && bytes[0] == (byte)0xEF && bytes[1] == (byte)0xBB && bytes[2] == (byte)0xBF) {
            return StandardCharsets.UTF_8;
        }
        
        // 2. Check for valid UTF-8 sequences
        int i = 0;
        boolean isUtf8 = true;
        while (i < bytes.length) {
            int b = bytes[i] & 0xFF;
            if (b < 0x80) {
                i++; // ASCII
            } else if ((b & 0xE0) == 0xC0) { // 2 bytes
                if (i + 1 >= bytes.length || (bytes[i + 1] & 0xC0) != 0x80) { isUtf8 = false; break; }
                i += 2;
            } else if ((b & 0xF0) == 0xE0) { // 3 bytes
                if (i + 2 >= bytes.length || (bytes[i + 1] & 0xC0) != 0x80 || (bytes[i + 2] & 0xC0) != 0x80) { isUtf8 = false; break; }
                i += 3;
            } else if ((b & 0xF8) == 0xF0) { // 4 bytes
                if (i + 3 >= bytes.length || (bytes[i + 1] & 0xC0) != 0x80 || (bytes[i + 2] & 0xC0) != 0x80 || (bytes[i + 3] & 0xC0) != 0x80) { isUtf8 = false; break; }
                i += 4;
            } else {
                isUtf8 = false;
                break;
            }
        }
        
        if (isUtf8) return StandardCharsets.UTF_8;
        
        // 3. Fallback to GBK
        try {
            return java.nio.charset.Charset.forName("GBK");
        } catch (Exception e) {
            return StandardCharsets.ISO_8859_1;
        }
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
    
    private String usernameFromAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        try {
            String token = authHeader.substring(7);
            Claims claims = jwtService.parse(token);
            return claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    // 1. Download Template
    @GetMapping("/template")
    public ResponseEntity<?> downloadTemplate(
            @RequestHeader("Authorization") String auth,
            @RequestParam("examId") Long examId,
            @RequestParam("classId") Long classId,
            @RequestParam("subject") String subject) {
        
        String role = roleFromAuth(auth);
        if (!"TEACHER".equals(role) && !"SCHOOL_ADMIN".equals(role) && !"SYSTEM_ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        // Fetch students
        List<User> students = userRepo.findByClassJoined_IdAndRole(classId, Role.STUDENT);
        
        StringBuilder csv = new StringBuilder();
        csv.append('\ufeff'); // BOM for Excel
        csv.append("Student ID,Student Name,Score\n");
        for (User s : students) {
            csv.append(s.getId()).append(",")
               .append(s.getUsername()).append(",")
               .append("\n"); // Empty score
        }
        
        String filename = "grade_template_class" + classId + "_" + subject + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(csv.toString());
    }

    // 2. Upload Grades
    @PostMapping("/upload")
    public ResponseEntity<?> uploadGrades(
            @RequestHeader("Authorization") String auth,
            @RequestParam("examId") Long examId,
            @RequestParam("classId") Long classId,
            @RequestParam("subject") String subject,
            @RequestParam("file") MultipartFile file) {
        
        String role = roleFromAuth(auth);
        String username = usernameFromAuth(auth);
        
        if (!"TEACHER".equals(role) && !"SCHOOL_ADMIN".equals(role) && !"SYSTEM_ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        
        // Verify teacher permission (Owner or Subject Teacher) if teacher
        if ("TEACHER".equals(role)) {
            Optional<SchoolClass> clsOpt = classRepo.findById(classId);
            if (clsOpt.isEmpty()) return ResponseEntity.notFound().build();
            SchoolClass cls = clsOpt.get();
            boolean isOwner = cls.getOwnerUsername() != null && cls.getOwnerUsername().equals(username);
            boolean isSubject = classTeacherRepo.existsBySchoolClass_IdAndTeacher_Username(classId, username);
            if (!isOwner && !isSubject) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "no_permission_for_class"));
            }
        }
        
        Long teacherId = userRepo.findByUsername(username).map(User::getId).orElse(null);

        try {
            byte[] bytes = file.getBytes();
            java.nio.charset.Charset charset = detectEncoding(bytes);
            
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new java.io.ByteArrayInputStream(bytes), charset))) {
                String line;
                List<ExamResult> results = new ArrayList<>();
                boolean header = true;
            while ((line = br.readLine()) != null) {
                if (header) { header = false; continue; } // Skip header
                String[] parts = line.split(",");
                if (parts.length < 3) continue;
                
                try {
                    Long studentId = Long.parseLong(parts[0].trim());
                    String studentName = parts[1].trim();
                    String scoreStr = parts[2].trim();
                    
                    Double score = null;
                    if (!scoreStr.isBlank() && !"-".equals(scoreStr)) {
                        score = Double.parseDouble(scoreStr);
                    }
                    
                    // Check existing
                    Optional<ExamResult> existing = resultRepo.findByExamIdAndStudentIdAndSubject(examId, studentId, subject);
                    ExamResult res = existing.orElse(new ExamResult(examId, studentId, studentName, subject, score, classId, teacherId));
                    if (existing.isPresent()) {
                        res.setScore(score);
                        res.setTeacherId(teacherId); // Update uploader
                    }
                    results.add(res);
                    
                } catch (Exception e) {
                    // Skip bad rows
                }
            }
            resultRepo.saveAll(results);
            return ResponseEntity.ok(Map.of("message", "Uploaded " + results.size() + " grades"));
            
        }} catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // 3. Confirm Grades
    @PostMapping("/confirm")
    public ResponseEntity<?> confirmGrades(
            @RequestHeader("Authorization") String auth,
            @RequestBody Map<String, Object> req) {
        
        String role = roleFromAuth(auth);
        String username = usernameFromAuth(auth);
        
        if (!"TEACHER".equals(role) && !"SCHOOL_ADMIN".equals(role) && !"SYSTEM_ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        
        Long examId = Long.valueOf(req.get("examId").toString());
        Long classId = Long.valueOf(req.get("classId").toString());
        String subject = req.get("subject").toString();

        // Verify permission
        if ("TEACHER".equals(role)) {
            Optional<SchoolClass> clsOpt = classRepo.findById(classId);
            if (clsOpt.isEmpty()) return ResponseEntity.notFound().build();
            SchoolClass cls = clsOpt.get();
            boolean isOwner = cls.getOwnerUsername() != null && cls.getOwnerUsername().equals(username);
            boolean isSubject = classTeacherRepo.existsBySchoolClass_IdAndTeacher_Username(classId, username);
            if (!isOwner && !isSubject) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "no_permission_for_class"));
            }
        }

        List<ExamResult> results = resultRepo.findByExamIdAndClassIdAndSubject(examId, classId, subject);
        if (results.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "no_grades_found"));
        }

        for (ExamResult r : results) {
            r.setPublished(true);
        }
        resultRepo.saveAll(results);

        // Notifications
        Optional<Exam> examOpt = examRepo.findById(examId);
        String examName = examOpt.map(Exam::getName).orElse("考试");
        String title = "成绩发布通知: " + examName + " - " + subject;
        String content = "您在 " + examName + " 中的 " + subject + " 成绩已发布，请查看。";
        
        // Notify Class Teacher (if not uploader)
        Optional<SchoolClass> clsOpt = classRepo.findById(classId);
        if (clsOpt.isPresent()) {
            String owner = clsOpt.get().getOwnerUsername();
            if (owner != null && !owner.equals(username)) {
                 notificationRepo.save(new Notification(owner, username, "班级成绩发布通知", 
                         username + " 老师已上传并确认了 " + clsOpt.get().getName() + " 的 " + subject + " 成绩。", "SYSTEM", null));
            }
        }
        
        // Notify Students & Parents
        for (ExamResult r : results) {
            // Find Student by User ID (r.getStudentId() is User ID)
            Optional<User> uOpt = userRepo.findById(r.getStudentId());
            if (uOpt.isPresent()) {
                String sUsername = uOpt.get().getUsername();
                // Notify Student
                notificationRepo.save(new Notification(sUsername, username, title, content, "SYSTEM", null));
                
                // Find Student entity for Parent
                List<Student> sEntities = studentRepo.findByUsername(sUsername);
                if (!sEntities.isEmpty()) {
                    Student sEntity = sEntities.get(0);
                    if (sEntity.getParentUser() != null) {
                        notificationRepo.save(new Notification(sEntity.getParentUser().getUsername(), username, title, 
                                "您的孩子 " + sEntity.getName() + " 的 " + subject + " 成绩已发布。", "SYSTEM", null));
                    }
                }
            }
        }

        return ResponseEntity.ok(Map.of("message", "confirmed", "count", results.size()));
    }

    // 3.5. Clear Grades
    @DeleteMapping("/clear")
    @Transactional
    public ResponseEntity<?> clearGrades(
            @RequestHeader("Authorization") String auth,
            @RequestParam("examId") Long examId,
            @RequestParam("classId") Long classId,
            @RequestParam("subject") String subject) {
        
        String role = roleFromAuth(auth);
        String username = usernameFromAuth(auth);
        
        if (!"TEACHER".equals(role) && !"SCHOOL_ADMIN".equals(role) && !"SYSTEM_ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        
        // Verify permission
        if ("TEACHER".equals(role)) {
            Optional<SchoolClass> clsOpt = classRepo.findById(classId);
            if (clsOpt.isEmpty()) return ResponseEntity.notFound().build();
            SchoolClass cls = clsOpt.get();
            boolean isOwner = cls.getOwnerUsername() != null && cls.getOwnerUsername().equals(username);
            boolean isSubject = classTeacherRepo.existsBySchoolClass_IdAndTeacher_Username(classId, username);
            if (!isOwner && !isSubject) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "no_permission_for_class"));
            }
        }
        
        resultRepo.deleteByExamIdAndClassIdAndSubject(examId, classId, subject);
        return ResponseEntity.ok(Map.of("message", "Grades cleared"));
    }

    // 4. View Grades (Student/Parent)
    @GetMapping("/my")
    public ResponseEntity<?> getMyGrades(
            @RequestHeader("Authorization") String auth,
            @RequestParam("examId") Long examId,
            @RequestParam(name = "studentId", required = false) Long studentId // Student Entity ID (for Parent)
            ) {
        
        String role = roleFromAuth(auth);
        String username = usernameFromAuth(auth);
        
        Long targetUserId = null; // User ID in ExamResult
        
        if ("STUDENT".equals(role)) {
            Optional<User> u = userRepo.findByUsername(username);
            if (u.isPresent()) targetUserId = u.get().getId();
        } else if ("PARENT".equals(role)) {
            if (studentId == null) {
                // Default to first child
                 Optional<User> pUser = userRepo.findByUsername(username);
                 if (pUser.isPresent()) {
                     List<Student> kids = studentRepo.findByParentUser_Id(pUser.get().getId());
                     if (!kids.isEmpty()) {
                         String kidUsername = kids.get(0).getUsername();
                         if (kidUsername != null) {
                             targetUserId = userRepo.findByUsername(kidUsername).map(User::getId).orElse(null);
                         }
                     }
                 }
            } else {
                // Verify parent-child
                Optional<Student> sOpt = studentRepo.findById(studentId);
                if (sOpt.isPresent()) {
                    Student s = sOpt.get();
                    if (s.getParentUser() != null && s.getParentUser().getUsername().equals(username)) {
                        String kidUsername = s.getUsername();
                        if (kidUsername != null) {
                            targetUserId = userRepo.findByUsername(kidUsername).map(User::getId).orElse(null);
                        }
                    } else {
                         return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                    }
                }
            }
        }
        
        if (targetUserId == null) return ResponseEntity.badRequest().body(Map.of("error", "student_not_found"));

        // Fetch Exam
        Optional<Exam> examOpt = examRepo.findById(examId);
        if (examOpt.isEmpty()) return ResponseEntity.notFound().build();
        Exam exam = examOpt.get();
        
        // Fetch Results
        List<ExamResult> results = resultRepo.findByStudentIdAndExamId(targetUserId, examId);
        
        // Construct response
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> gradeList = new ArrayList<>();
        
        List<String> subjects = exam.getSubjects();
        if (subjects == null || subjects.isEmpty()) subjects = List.of("语文", "数学", "英语", "科学", "道法");
        
        for (String subj : subjects) {
            Map<String, Object> item = new HashMap<>();
            item.put("subject", subj);
            
            Optional<ExamResult> res = results.stream()
                .filter(r -> r.getSubject().equals(subj) && r.isPublished())
                .findFirst();
            
            if (res.isPresent()) {
                item.put("score", res.get().getScore());
            } else {
                item.put("score", "-");
            }
            gradeList.add(item);
        }
        
        response.put("examName", exam.getName());
        response.put("grades", gradeList);
        
        return ResponseEntity.ok(response);
    }

    // 5. View Grades (Teacher Class View)
    @GetMapping("/class/{classId}")
    public ResponseEntity<?> getClassGrades(
            @RequestHeader("Authorization") String auth,
            @PathVariable("classId") Long classId,
            @RequestParam("examId") Long examId) {
        
        String role = roleFromAuth(auth);
        String username = usernameFromAuth(auth);
        
        if (!"TEACHER".equals(role) && !"SCHOOL_ADMIN".equals(role) && !"SYSTEM_ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<ExamResult> allResults = resultRepo.findByExamIdAndClassId(examId, classId);
        
        // Filter based on role
            if ("TEACHER".equals(role)) {
                Optional<SchoolClass> clsOpt = classRepo.findById(classId);
                if (clsOpt.isPresent()) {
                    SchoolClass cls = clsOpt.get();
                    boolean isOwner = cls.getOwnerUsername() != null && cls.getOwnerUsername().equals(username);
                    boolean isTeacher = classTeacherRepo.existsBySchoolClass_IdAndTeacher_Username(classId, username);
                    
                    if (!isOwner && !isTeacher) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                    }
                    
                    // Allow all teachers in the class to see all grades (Merged view)
                    // Previously filtered by subject, but this prevents seeing other subjects' grades
                }
            }
        
        return ResponseEntity.ok(allResults);
    }

    // 4. View Grades (Parent/Student)
    @GetMapping("/student/{studentId}")
    public ResponseEntity<?> getStudentGrades(
            @RequestHeader("Authorization") String auth,
            @PathVariable("studentId") Long studentId,
            @RequestParam(name = "examId", required = false) Long examId) {
        
        String role = roleFromAuth(auth);
        String username = usernameFromAuth(auth);
        
        // Verify access
        if ("PARENT".equals(role)) {
            // Check if bound to this student
            Optional<User> parent = userRepo.findByUsername(username);
            if (parent.isEmpty() || !studentId.equals(parent.get().getBoundStudentId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        } else if ("STUDENT".equals(role)) {
            Optional<User> student = userRepo.findByUsername(username);
            if (student.isEmpty() || !student.get().getId().equals(studentId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        } else if (!"TEACHER".equals(role) && !"SCHOOL_ADMIN".equals(role) && !"SYSTEM_ADMIN".equals(role)) {
             return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<ExamResult> results;
        if (examId != null) {
            results = resultRepo.findByStudentIdAndExamId(studentId, examId);
        } else {
            results = resultRepo.findByStudentId(studentId);
        }
        return ResponseEntity.ok(results);
    }
    
    // 5. Analysis (Admin)
    @GetMapping("/analysis/exam/{examId}")
    public ResponseEntity<?> getExamAnalysis(
             @RequestHeader("Authorization") String auth,
             @PathVariable("examId") Long examId) {
         String role = roleFromAuth(auth);
         if (!"SCHOOL_ADMIN".equals(role) && !"SYSTEM_ADMIN".equals(role)) {
             return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
         }
         
         List<ExamResult> all = resultRepo.findByExamId(examId);
         return ResponseEntity.ok(all);
    }

    // 6. Export Grades (Teacher - Single Class/Subject or All)
    @GetMapping("/export/teacher")
    public ResponseEntity<?> exportTeacherGrades(
            @RequestHeader("Authorization") String auth,
            @RequestParam("examId") Long examId,
            @RequestParam("classId") Long classId,
            @RequestParam(name = "subject", required = false) String subject) {
        
        String role = roleFromAuth(auth);
        String username = usernameFromAuth(auth);
        
        if (!"TEACHER".equals(role) && !"SCHOOL_ADMIN".equals(role) && !"SYSTEM_ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Verify permission and get accessible subjects
        List<String> accessibleSubjects = new ArrayList<>();
        boolean isOwner = false;
        
        if ("TEACHER".equals(role)) {
            Optional<SchoolClass> clsOpt = classRepo.findById(classId);
            if (clsOpt.isEmpty()) return ResponseEntity.notFound().build();
            SchoolClass cls = clsOpt.get();
            isOwner = cls.getOwnerUsername() != null && cls.getOwnerUsername().equals(username);
            
            if (!isOwner) {
                List<ClassTeacher> cts = classTeacherRepo.findBySchoolClass_IdAndTeacher_Username(classId, username);
                if (cts.isEmpty()) {
                     return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "no_permission"));
                }
                accessibleSubjects = cts.stream().map(ClassTeacher::getSubject).collect(Collectors.toList());
            }
        }
        
        List<ExamResult> results = resultRepo.findByExamIdAndClassId(examId, classId);
        
        // Filter by role/permission
        if ("TEACHER".equals(role) && !isOwner) {
            List<String> finalAccessibleSubjects = accessibleSubjects;
            results = results.stream()
                .filter(r -> finalAccessibleSubjects.contains(r.getSubject()))
                .collect(Collectors.toList());
        }
        
        // Filter by requested subject if present
        if (subject != null && !subject.isBlank()) {
            results = results.stream()
                .filter(r -> r.getSubject().equals(subject))
                .collect(Collectors.toList());
        }
        
        if (results.isEmpty()) {
             return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"no_data.txt\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body("No grades found for export.");
        }
        
        StringBuilder csv = new StringBuilder();
        csv.append('\ufeff'); // BOM
        
        // Group by Subject
        Map<String, List<ExamResult>> bySubject = results.stream()
            .collect(Collectors.groupingBy(ExamResult::getSubject));
            
        csv.append("Analysis Report\n\n");
        
        // Summary Table
        csv.append("Subject,Count,Average,Max,Min\n");
        for (Map.Entry<String, List<ExamResult>> entry : bySubject.entrySet()) {
            DoubleSummaryStatistics stats = entry.getValue().stream()
                .filter(r -> r.getScore() != null)
                .mapToDouble(ExamResult::getScore)
                .summaryStatistics();
            
            if (stats.getCount() > 0) {
                csv.append(entry.getKey()).append(",")
                   .append(stats.getCount()).append(",")
                   .append(String.format("%.2f", stats.getAverage())).append(",")
                   .append(stats.getMax()).append(",")
                   .append(stats.getMin()).append("\n");
            } else {
                 csv.append(entry.getKey()).append(",0,-,-,-\n");
            }
        }
        
        csv.append("\nDetailed Grades\n");
        csv.append("Subject,Student ID,Student Name,Score,Class Rank\n");
        
        for (Map.Entry<String, List<ExamResult>> entry : bySubject.entrySet()) {
            List<ExamResult> subList = entry.getValue();
            subList.sort((a, b) -> {
                if (a.getScore() == null) return 1;
                if (b.getScore() == null) return -1;
                return Double.compare(b.getScore(), a.getScore());
            });
            
            int rank = 1;
            for (ExamResult r : subList) {
                csv.append(r.getSubject()).append(",")
                   .append(r.getStudentId()).append(",")
                   .append(r.getStudentName()).append(",")
                   .append(r.getScore() == null ? "-" : r.getScore()).append(",")
                   .append(r.getScore() == null ? "-" : rank++).append("\n");
            }
        }
        
        String filename = "analysis_class" + classId + (subject != null ? "_" + subject : "_all") + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(csv.toString());
    }

    // 7. Export Analysis (Admin - Whole Exam)
    @GetMapping("/export/admin")
    public ResponseEntity<?> exportAdminAnalysis(
             @RequestHeader("Authorization") String auth,
             @RequestParam("examId") Long examId) {
         
         String role = roleFromAuth(auth);
         if (!"SCHOOL_ADMIN".equals(role) && !"SYSTEM_ADMIN".equals(role)) {
             return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
         }
         
         List<ExamResult> all = resultRepo.findByExamId(examId);
         Map<Long, String> classNames = classRepo.findAll().stream()
             .collect(Collectors.toMap(SchoolClass::getId, SchoolClass::getName));
         
         StringBuilder csv = new StringBuilder();
         csv.append('\ufeff');
         
         // Summary Section
         csv.append("Analysis Summary\n");
         csv.append("Class,Subject,Count,Average,Max,Min\n");
         
         Map<Long, Map<String, List<ExamResult>>> byClassSubject = all.stream()
             .collect(Collectors.groupingBy(
                 ExamResult::getClassId,
                 Collectors.groupingBy(ExamResult::getSubject)
             ));
             
         List<Long> sortedClassIds = new ArrayList<>(byClassSubject.keySet());
         Collections.sort(sortedClassIds);
         
         for (Long cid : sortedClassIds) {
             String cName = classNames.getOrDefault(cid, "Unknown Class " + cid);
             Map<String, List<ExamResult>> subjMap = byClassSubject.get(cid);
             
             for (Map.Entry<String, List<ExamResult>> entry : subjMap.entrySet()) {
                 String subj = entry.getKey();
                 List<ExamResult> list = entry.getValue();
                 
                 DoubleSummaryStatistics stats = list.stream()
                     .filter(r -> r.getScore() != null)
                     .mapToDouble(ExamResult::getScore)
                     .summaryStatistics();
                 
                 if (stats.getCount() > 0) {
                     csv.append(cName).append(",")
                        .append(subj).append(",")
                        .append(stats.getCount()).append(",")
                        .append(String.format("%.2f", stats.getAverage())).append(",")
                        .append(stats.getMax()).append(",")
                        .append(stats.getMin()).append("\n");
                 } else {
                     csv.append(cName).append(",")
                        .append(subj).append(",")
                        .append("0,-,-,-\n");
                 }
             }
         }
         
         csv.append("\nDetailed Grades\n");
         csv.append("Class,Student ID,Student Name,Subject,Score,Grade Rank\n");
         
         // Group by subject
         Map<String, List<ExamResult>> bySubject = all.stream().collect(Collectors.groupingBy(ExamResult::getSubject));
         
         for (String subj : bySubject.keySet()) {
             List<ExamResult> subList = bySubject.get(subj);
             subList.sort((a, b) -> {
                 if (a.getScore() == null) return 1;
                 if (b.getScore() == null) return -1;
                 return Double.compare(b.getScore(), a.getScore());
             });
             
             int rank = 1;
             for (ExamResult r : subList) {
                 csv.append(classNames.getOrDefault(r.getClassId(), "Unknown")).append(",")
                    .append(r.getStudentId()).append(",")
                    .append(r.getStudentName()).append(",")
                    .append(r.getSubject()).append(",")
                    .append(r.getScore() == null ? "-" : r.getScore()).append(",")
                    .append(r.getScore() == null ? "-" : rank++).append("\n");
             }
         }
         
         return ResponseEntity.ok()
                 .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"exam_" + examId + "_analysis.csv\"")
                 .contentType(MediaType.TEXT_PLAIN)
                 .body(csv.toString());
    }
}
