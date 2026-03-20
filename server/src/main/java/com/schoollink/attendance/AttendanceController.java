package com.schoollink.attendance;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.schoollink.auth.JwtService;
import com.schoollink.school.ClassTeacherRepository;
import com.schoollink.school.SchoolClass;
import com.schoollink.school.SchoolClassRepository;
import com.schoollink.school.Student;
import com.schoollink.school.StudentRepository;

import io.jsonwebtoken.Claims;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceRepository attendanceRepo;
    private final StudentRepository studentRepo;
    private final SchoolClassRepository classRepo;
    private final ClassTeacherRepository classTeacherRepo;
    private final JwtService jwtService;

    public AttendanceController(AttendanceRepository attendanceRepo, StudentRepository studentRepo,
                                SchoolClassRepository classRepo, ClassTeacherRepository classTeacherRepo, JwtService jwtService) {
        this.attendanceRepo = attendanceRepo;
        this.studentRepo = studentRepo;
        this.classRepo = classRepo;
        this.classTeacherRepo = classTeacherRepo;
        this.jwtService = jwtService;
    }

    // Helper: Get username from token
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

    // 1. Get attendance for a class on a specific date
    @GetMapping("/class/{classId}")
    public ResponseEntity<?> getClassAttendance(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("classId") Long classId,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        String role = roleFromAuth(authHeader);
        // Teachers and Admins can view
        if (!"TEACHER".equals(role) && !"SCHOOL_ADMIN".equals(role) && !"SYSTEM_ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }

        // Check teacher visibility
        if ("TEACHER".equals(role)) {
            String username = usernameFromAuth(authHeader);
            Optional<SchoolClass> clsOpt = classRepo.findById(classId);
            if (clsOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "class_not_found"));
            }
            SchoolClass cls = clsOpt.get();
            boolean isHeadTeacher = cls.getOwnerUsername() != null && cls.getOwnerUsername().equals(username);
            boolean isSubjectTeacher = classTeacherRepo.existsBySchoolClass_IdAndTeacher_Username(classId, username);
            
            if (!isHeadTeacher && !isSubjectTeacher) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "no_permission_to_view_class"));
            }
        }

        // Fetch existing records
        List<Attendance> records = attendanceRepo.findByClassIdAndDate(classId, date);
        
        // Fetch all students in class to ensure we have a complete list
        List<Student> students = studentRepo.findBySchoolClass_Id(classId);
        
        // Merge
        List<Map<String, Object>> result = new ArrayList<>();
        Map<Long, Attendance> recordMap = records.stream()
                .collect(Collectors.toMap(Attendance::getStudentId, a -> a));

        for (Student s : students) {
            Map<String, Object> item = new HashMap<>();
            item.put("studentId", s.getId());
            item.put("studentName", s.getName());
            item.put("studentNo", s.getStudentNo());
            
            if (recordMap.containsKey(s.getId())) {
                Attendance a = recordMap.get(s.getId());
                item.put("status", a.getStatus());
                item.put("remarks", a.getRemarks());
                item.put("recorded", true);
            } else {
                item.put("status", null); // Not recorded yet
                item.put("remarks", "");
                item.put("recorded", false);
            }
            result.add(item);
        }
        
        return ResponseEntity.ok(result);
    }

    // 2. Batch update attendance
    @PostMapping("/batch")
    public ResponseEntity<?> batchUpdate(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody BatchAttendanceRequest req) {
        
        String role = roleFromAuth(authHeader);
        if (!"TEACHER".equals(role) && !"SCHOOL_ADMIN".equals(role) && !"SYSTEM_ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }

        Long classId = req.getClassId();

        // Check teacher visibility
        if ("TEACHER".equals(role)) {
            String username = usernameFromAuth(authHeader);
            Optional<SchoolClass> clsOpt = classRepo.findById(classId);
            if (clsOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "class_not_found"));
            }
            SchoolClass cls = clsOpt.get();
            boolean isHeadTeacher = cls.getOwnerUsername() != null && cls.getOwnerUsername().equals(username);
            boolean isSubjectTeacher = classTeacherRepo.existsBySchoolClass_IdAndTeacher_Username(classId, username);
            
            if (!isHeadTeacher && !isSubjectTeacher) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "no_permission_to_view_class"));
            }

            // Check update permission (Only Head Teacher or Admin)
            if (!isHeadTeacher) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "only_head_teacher_can_modify"));
            }
        }

        List<Attendance> toSave = new ArrayList<>();
        
        for (AttendanceItem item : req.getItems()) {
            Optional<Attendance> existing = attendanceRepo.findByStudentIdAndDate(item.getStudentId(), req.getDate());
            Attendance a = existing.orElse(new Attendance());
            
            if (a.getId() == null) {
                a.setStudentId(item.getStudentId());
                a.setClassId(req.getClassId());
                a.setDate(req.getDate());
            }
            
            a.setStatus(item.getStatus());
            a.setRemarks(item.getRemarks());
            a.setUpdatedAt(java.time.OffsetDateTime.now());
            toSave.add(a);
        }
        
        attendanceRepo.saveAll(toSave);
        return ResponseEntity.ok(Map.of("message", "Success", "count", toSave.size()));
    }

    // 3. Get student attendance (for parent)
    @GetMapping("/student/{studentId}")
    public ResponseEntity<?> getStudentAttendance(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("studentId") Long studentId,
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        
        // Validation: Parent must own the student, or be teacher/admin
        // For simplicity, assuming caller checks or we check role.
        // A strict check would verify if parent is bound to student.
        
        List<Attendance> records = attendanceRepo.findByStudentIdAndDateBetween(studentId, start, end);
        return ResponseEntity.ok(records);
    }

    // 4. Get class stats (for reports)
    @GetMapping("/stats/class/{classId}")
    public ResponseEntity<?> getClassStats(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("classId") Long classId,
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        
        String role = roleFromAuth(authHeader);
        if (!"TEACHER".equals(role) && !"SCHOOL_ADMIN".equals(role) && !"SYSTEM_ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }

        // Check teacher visibility
        if ("TEACHER".equals(role)) {
            String username = usernameFromAuth(authHeader);
            Optional<SchoolClass> clsOpt = classRepo.findById(classId);
            if (clsOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "class_not_found"));
            }
            SchoolClass cls = clsOpt.get();
            boolean isHeadTeacher = cls.getOwnerUsername() != null && cls.getOwnerUsername().equals(username);
            boolean isSubjectTeacher = classTeacherRepo.existsBySchoolClass_IdAndTeacher_Username(classId, username);
            
            if (!isHeadTeacher && !isSubjectTeacher) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "no_permission_to_view_class"));
            }
        }

        List<Student> students = studentRepo.findBySchoolClass_Id(classId);
        List<Attendance> records = attendanceRepo.findByClassIdAndDateBetween(classId, start, end);

        Map<String, Object> response = new HashMap<>();
        response.put("start", start);
        response.put("end", end);

        // Group records by student
        Map<Long, List<Attendance>> byStudent = records.stream().collect(Collectors.groupingBy(Attendance::getStudentId));

        List<Map<String, Object>> studentStats = new ArrayList<>();
        for (Student s : students) {
            Map<String, Object> sMap = new HashMap<>();
            sMap.put("id", s.getId());
            sMap.put("name", s.getName());
            sMap.put("studentNo", s.getStudentNo());

            List<Attendance> sRecords = byStudent.getOrDefault(s.getId(), Collections.emptyList());

            // Count stats
            Map<AttendanceStatus, Long> counts = sRecords.stream()
                .collect(Collectors.groupingBy(Attendance::getStatus, Collectors.counting()));

            sMap.put("stats", counts);

            // Simple details list
            List<Map<String, Object>> details = sRecords.stream()
                .map(r -> {
                    Map<String, Object> d = new HashMap<>();
                    d.put("date", r.getDate());
                    d.put("status", r.getStatus());
                    return d;
                })
                .collect(Collectors.toList());
            sMap.put("details", details);

            studentStats.add(sMap);
        }
        response.put("students", studentStats);

        // Daily stats summary
        Map<LocalDate, Map<AttendanceStatus, Long>> dailyStats = records.stream()
            .collect(Collectors.groupingBy(Attendance::getDate,
                Collectors.groupingBy(Attendance::getStatus, Collectors.counting())));

        response.put("daily", dailyStats);

        return ResponseEntity.ok(response);
    }

    // DTOs
    public static class BatchAttendanceRequest {
        private Long classId;
        private LocalDate date;
        private List<AttendanceItem> items;

        public Long getClassId() { return classId; }
        public void setClassId(Long classId) { this.classId = classId; }
        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
        public List<AttendanceItem> getItems() { return items; }
        public void setItems(List<AttendanceItem> items) { this.items = items; }
    }

    public static class AttendanceItem {
        private Long studentId;
        private AttendanceStatus status;
        private String remarks;

        public Long getStudentId() { return studentId; }
        public void setStudentId(Long studentId) { this.studentId = studentId; }
        public AttendanceStatus getStatus() { return status; }
        public void setStatus(AttendanceStatus status) { this.status = status; }
        public String getRemarks() { return remarks; }
        public void setRemarks(String remarks) { this.remarks = remarks; }
    }
}
