package com.schoollink.leave;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
import org.springframework.transaction.annotation.Transactional;

import com.schoollink.attendance.Attendance;
import com.schoollink.attendance.AttendanceRepository;
import com.schoollink.attendance.AttendanceStatus;
import com.schoollink.auth.JwtService;
import com.schoollink.school.SchoolClass;
import com.schoollink.school.SchoolClassRepository;
import com.schoollink.school.Student;
import com.schoollink.school.StudentRepository;
import com.schoollink.user.User;
import com.schoollink.user.UserService;

import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class LeaveController {
    private final LeaveRepository leaveRepo;
    private final JwtService jwtService;
    private final UserService userService;
    private final StudentRepository studentRepo;
    private final SchoolClassRepository classRepo;
    private final AttendanceRepository attendanceRepo;

    public LeaveController(LeaveRepository leaveRepo, JwtService jwtService, UserService userService,
            StudentRepository studentRepo, SchoolClassRepository classRepo, AttendanceRepository attendanceRepo) {
        this.leaveRepo = leaveRepo;
        this.jwtService = jwtService;
        this.userService = userService;
        this.studentRepo = studentRepo;
        this.classRepo = classRepo;
        this.attendanceRepo = attendanceRepo;
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

    private boolean isParent(String role) {
        return "PARENT".equals(role);
    }

    private boolean isTeacherOrAdmin(String role) {
        return role != null && (role.equals("TEACHER") || role.equals("SCHOOL_ADMIN") || role.equals("SYSTEM_ADMIN"));
    }

    @PostMapping("/leaves")
    public ResponseEntity<?> submitLeave(@RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody LeaveRequest req) {
        String role = roleFromAuth(authorization);
        if (!isParent(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        
        Optional<Student> studentOpt = studentRepo.findById(req.getStudentId());
        if (studentOpt.isPresent() && studentOpt.get().getSchoolClass() != null) {
            req.setClassId(studentOpt.get().getSchoolClass().getId());
        }

        LeaveRequest saved = leaveRepo.save(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/leaves")
    public ResponseEntity<?> listMyLeaves(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String role = roleFromAuth(authorization);
        if (!isParent(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        String username;
        try {
            String token = authorization.substring(7);
            Claims claims = jwtService.parse(token);
            username = claims.getSubject();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<User> userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty() || userOpt.get().getBoundStudentId() == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(leaveRepo.findByStudentId(userOpt.get().getBoundStudentId()));
    }

    @GetMapping("/teacher/leaves")
    public ResponseEntity<?> listTeacherLeaves(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String role = roleFromAuth(authorization);
        if (!isTeacherOrAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        
        String username;
        try {
            String token = authorization.substring(7);
            Claims claims = jwtService.parse(token);
            username = claims.getSubject();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<SchoolClass> myClasses = classRepo.findByOwnerUsername(username);
        if (myClasses.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<Long> classIds = myClasses.stream().map(SchoolClass::getId).collect(Collectors.toList());
        return ResponseEntity.ok(leaveRepo.findByClassIdInOrderByCreatedAtDesc(classIds));
    }

    @GetMapping("/classes/{classId}/leave")
    public ResponseEntity<?> listLeaveByClass(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId) {
        String role = roleFromAuth(authorization);
        if (!isTeacherOrAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        
        // Verify ownership (only class teacher can see leaves)
        String username;
        try {
            String token = authorization.substring(7);
            Claims claims = jwtService.parse(token);
            username = claims.getSubject();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<SchoolClass> clsOpt = classRepo.findById(classId);
        if (clsOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "class_not_found"));
        }
        
        if (!clsOpt.get().getOwnerUsername().equals(username)) {
            // If not owner, check if admin? Assuming only class teacher as per request.
            // "每个班家长提交的请假内容只有班主任才能看到"
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "only_class_teacher_can_view_leaves"));
        }

        return ResponseEntity.ok(leaveRepo.findByClassId(classId));
    }

    @PutMapping("/leave/{id}/approve")
    @Transactional
    public ResponseEntity<?> approve(@RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id) {
        String role = roleFromAuth(authorization);
        if (!isTeacherOrAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        return leaveRepo.findById(id).<ResponseEntity<Object>>map(l -> {
            l.setStatus(LeaveStatus.APPROVED);
            l.setDecidedAt(OffsetDateTime.now());
            l.setDecidedByRole(role);
            LeaveRequest saved = leaveRepo.save(l);
            
            // Sync to Attendance
            if (saved.getDateFrom() != null && saved.getDateTo() != null) {
                java.time.LocalDate d = saved.getDateFrom();
                while (!d.isAfter(saved.getDateTo())) {
                    java.util.Optional<Attendance> attOpt = attendanceRepo.findByStudentIdAndDate(saved.getStudentId(), d);
                    Attendance att = attOpt.orElse(new Attendance());
                    if (att.getId() == null) {
                        att.setStudentId(saved.getStudentId());
                        att.setClassId(saved.getClassId() != null ? saved.getClassId() : 0L); // Ensure classId
                        att.setDate(d);
                    }
                    
                    if (saved.getType() == LeaveType.SICK) {
                        att.setStatus(AttendanceStatus.SICK);
                    } else {
                        att.setStatus(AttendanceStatus.PERSONAL);
                    }
                    att.setRemarks("Leave Approved: " + saved.getReason());
                    att.setUpdatedAt(OffsetDateTime.now());
                    attendanceRepo.save(att);
                    
                    d = d.plusDays(1);
                }
            }
            
            return ResponseEntity.ok((Object) saved);
        }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found")));
    }

    @PutMapping("/leave/{id}/reject")
    @Transactional
    public ResponseEntity<?> reject(@RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id) {
        String role = roleFromAuth(authorization);
        if (!isTeacherOrAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        return leaveRepo.findById(id).<ResponseEntity<Object>>map(l -> {
            l.setStatus(LeaveStatus.REJECTED);
            l.setDecidedAt(OffsetDateTime.now());
            l.setDecidedByRole(role);
            return ResponseEntity.ok((Object) leaveRepo.save(l));
        }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found")));
    }
}
