package com.schoollink.notice;

import java.util.Arrays;
import java.util.Collections;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.schoollink.auth.JwtService;
import com.schoollink.school.Student;
import com.schoollink.school.StudentRepository;
import com.schoollink.system.SystemConfig;
import com.schoollink.system.SystemConfigRepo;
import com.schoollink.user.UserRepository;

import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class NoticeController {
    private final NoticeRepository noticeRepo;
    private final NoticeSignUpRepository signUpRepo;
    private final UserRepository userRepo;
    private final JwtService jwtService;
    private final SystemConfigRepo configRepo;
    private final StudentRepository studentRepo;

    public NoticeController(NoticeRepository noticeRepo, NoticeSignUpRepository signUpRepo, UserRepository userRepo,
            JwtService jwtService, SystemConfigRepo configRepo, StudentRepository studentRepo) {
        this.noticeRepo = noticeRepo;
        this.signUpRepo = signUpRepo;
        this.userRepo = userRepo;
        this.jwtService = jwtService;
        this.configRepo = configRepo;
        this.studentRepo = studentRepo;
    }

    private boolean isModuleEnabled() {
        return configRepo.findById(1L).map(SystemConfig::isModuleNoticeEnabled).orElse(true);
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

    private boolean canPublish(String role) {
        return role != null && (role.equals("SYSTEM_ADMIN") || role.equals("SCHOOL_ADMIN") || role.equals("TEACHER"));
    }

    @PostMapping("/notices")
    public ResponseEntity<?> createNotice(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody Notice req) {
        if (!isModuleEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "module_disabled"));
        }
        String role = roleFromAuth(authorization);
        if (!canPublish(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        if (req.getScope() == NoticeScope.CLASS && (req.getClassId() == null)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "class_required"));
        }
        req.setCreatedByRole(role);
        Notice saved = noticeRepo.save(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/notices")
    public List<Notice> listNotices(@RequestParam(value = "scope", required = false) NoticeScope scope,
            @RequestParam(value = "classId", required = false) Long classId) {
        if (scope == null)
            return noticeRepo.findAll();
        if (scope == NoticeScope.GLOBAL)
            return noticeRepo.findByScope(NoticeScope.GLOBAL);
        if (scope == NoticeScope.SCHOOL)
            return noticeRepo.findByScope(NoticeScope.SCHOOL);
        if (classId != null)
            return noticeRepo.findByClassId(classId);
        return noticeRepo.findByScope(NoticeScope.CLASS);
    }

    @GetMapping("/notices/feed")
    public ResponseEntity<?> getNoticeFeed(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer "))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String role = roleFromAuth(authorization);
        String username;
        try {
            Claims claims = jwtService.parse(authorization.substring(7));
            username = claims.getSubject();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<NoticeScope> globalScopes = Arrays.asList(NoticeScope.GLOBAL, NoticeScope.SCHOOL);

        if ("STUDENT".equals(role)) {
            List<Student> students = studentRepo.findByUsername(username);
            if (students.isEmpty()) {
                return ResponseEntity.ok(noticeRepo.findByScopeIn(globalScopes));
            }
            Long classId = students.get(0).getSchoolClass() != null ? students.get(0).getSchoolClass().getId() : null;
            if (classId == null)
                return ResponseEntity.ok(noticeRepo.findByScopeIn(globalScopes));
            return ResponseEntity.ok(noticeRepo.findForFeed(globalScopes, Collections.singletonList(classId)));
        } else if ("PARENT".equals(role)) {
            Optional<com.schoollink.user.User> pUser = userRepo.findByUsername(username);
            if (pUser.isEmpty())
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            com.schoollink.user.User user = pUser.get();

            Long classId = null;
            // Priority: Check boundStudentId first (as per UI binding)
            if (user.getBoundStudentId() != null) {
                Optional<Student> sOpt = studentRepo.findById(user.getBoundStudentId());
                if (sOpt.isPresent() && sOpt.get().getSchoolClass() != null) {
                    classId = sOpt.get().getSchoolClass().getId();
                }
            }

            // Fallback: Check parentUser relationship
            if (classId == null) {
                List<Student> kids = studentRepo.findByParentUser_Id(user.getId());
                for (Student s : kids) {
                    if (s.getSchoolClass() != null) {
                        classId = s.getSchoolClass().getId();
                        break;
                    }
                }
            }

            if (classId == null)
                return ResponseEntity.ok(noticeRepo.findByScopeIn(globalScopes));
            return ResponseEntity.ok(noticeRepo.findForFeed(globalScopes, Collections.singletonList(classId)));
        } else {
            // Teacher / Admin
            return ResponseEntity.ok(noticeRepo.findByScopeIn(globalScopes));
        }
    }

    @GetMapping("/classes/{classId}/notices")
    public List<Notice> listClassNotices(@PathVariable("classId") Long classId) {
        return noticeRepo.findByClassId(classId);
    }

    @PostMapping("/notices/{id}/signup")
    public ResponseEntity<?> signUp(@RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id) {
        if (authorization == null || !authorization.startsWith("Bearer "))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String username;
        try {
            Claims claims = jwtService.parse(authorization.substring(7));
            username = claims.getSubject();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<com.schoollink.user.User> userOpt = userRepo.findByUsername(username);
        if (userOpt.isEmpty())
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Long userId = userOpt.get().getId();

        Optional<Notice> nOpt = noticeRepo.findById(id);
        if (nOpt.isEmpty())
            return ResponseEntity.notFound().build();
        Notice n = nOpt.get();

        if (!n.isAllowSignUp()) {
            return ResponseEntity.badRequest().body(Map.of("error", "signup_not_allowed"));
        }

        if (signUpRepo.findByNoticeIdAndUserId(id, userId).isPresent()) {
            return ResponseEntity.ok(Map.of("message", "already_signed_up"));
        }

        NoticeSignUp ns = new NoticeSignUp(id, userId);
        signUpRepo.save(ns);
        return ResponseEntity.ok(Map.of("message", "signed_up"));
    }

    @GetMapping("/notices/{id}/signups")
    public ResponseEntity<?> listSignUps(@RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id) {
        String role = roleFromAuth(authorization);
        if (!canPublish(role)) { // Only admins/teachers can view signups
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }

        List<NoticeSignUp> list = signUpRepo.findByNoticeId(id);
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (NoticeSignUp ns : list) {
            String uName = "Unknown";
            Optional<com.schoollink.user.User> u = userRepo.findById(ns.getUserId());
            if (u.isPresent())
                uName = u.get().getUsername();
            result.add(Map.of(
                    "userId", ns.getUserId(),
                    "username", uName,
                    "signedUpAt", ns.getSignedUpAt()));
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/notices/{id}/my-signup")
    public ResponseEntity<?> checkMySignUp(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id) {
        if (authorization == null || !authorization.startsWith("Bearer "))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String username;
        try {
            Claims claims = jwtService.parse(authorization.substring(7));
            username = claims.getSubject();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Optional<com.schoollink.user.User> userOpt = userRepo.findByUsername(username);
        if (userOpt.isEmpty())
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        boolean signed = signUpRepo.findByNoticeIdAndUserId(id, userOpt.get().getId()).isPresent();
        return ResponseEntity.ok(Map.of("signedUp", signed));
    }
}
