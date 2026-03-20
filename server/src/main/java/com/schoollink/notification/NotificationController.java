package com.schoollink.notification;

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
import com.schoollink.school.Student;
import com.schoollink.school.StudentRepository;
import com.schoollink.user.UserService;
import com.schoollink.system.SystemConfig;
import com.schoollink.system.SystemConfigRepo;

import io.jsonwebtoken.Claims;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationRepository notificationRepo;
    private final JwtService jwtService;
    private final UserService userService;
    private final StudentRepository studentRepo;
    private final SystemConfigRepo configRepo;

    public NotificationController(NotificationRepository notificationRepo, JwtService jwtService,
            UserService userService, StudentRepository studentRepo, SystemConfigRepo configRepo) {
        this.notificationRepo = notificationRepo;
        this.jwtService = jwtService;
        this.userService = userService;
        this.studentRepo = studentRepo;
        this.configRepo = configRepo;
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

    private boolean isAssignmentModuleEnabled() {
        boolean enabled = configRepo.findById(1L).map(SystemConfig::isModuleAssignmentEnabled).orElse(true);
        System.out.println("DEBUG: NotificationController.isAssignmentModuleEnabled: " + enabled);
        return enabled;
    }

    @GetMapping("/my")
    public ResponseEntity<?> listMyNotifications(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String username = usernameFromAuth(authorization);
        if (username == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        List<Notification> list = notificationRepo.findByReceiverUsernameOrderByCreatedAtDesc(username);

        // Filter out ASSIGNMENT type if module disabled
        if (!isAssignmentModuleEnabled()) {
            list.removeIf(n -> "ASSIGNMENT".equals(n.getType()) || "ASSIGNMENT_RETURNED".equals(n.getType()));
        }

        return ResponseEntity.ok(list);
    }

    @GetMapping("/sent")
    public ResponseEntity<?> listSentNotifications(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String username = usernameFromAuth(authorization);
        if (username == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(notificationRepo.findBySenderUsernameOrderByCreatedAtDesc(username));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id) {
        String username = usernameFromAuth(authorization);
        if (username == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Optional<Notification> notifOpt = notificationRepo.findById(id);
        if (notifOpt.isEmpty())
            return ResponseEntity.notFound().build();

        Notification notif = notifOpt.get();
        if (!notif.getReceiverUsername().equals(username)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        notif.setRead(true);
        notificationRepo.save(notif);
        return ResponseEntity.ok().build();
    }

    // Send Notification (Teacher/Admin)
    @PostMapping("/send")
    public ResponseEntity<?> sendNotification(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody SendNotificationRequest req) {
        String senderRole = roleFromAuth(authorization);
        String senderUsername = usernameFromAuth(authorization);

        if (senderRole == null || (!senderRole.equals("TEACHER") && !senderRole.equals("SCHOOL_ADMIN")
                && !senderRole.equals("SYSTEM_ADMIN"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }

        if (req.getClassId() == null && (req.getReceiverUsername() == null || req.getReceiverUsername().isBlank())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Must specify classId or receiverUsername"));
        }

        int count = 0;

        // Send to whole class
        if (req.getClassId() != null && (req.getReceiverUsername() == null || req.getReceiverUsername().isBlank())) {
            List<Student> students = studentRepo.findBySchoolClass_Id(req.getClassId());
            for (Student s : students) {
                if (s.getUsername() != null) {
                    Notification n = new Notification(s.getUsername(), senderUsername, req.getTitle(), req.getContent(),
                            "MESSAGE", null);
                    notificationRepo.save(n);
                    count++;
                } else {
                    // System.out.println("Skipping student " + s.getName() + " (no username)");
                }
            }
        } else if (req.getReceiverUsername() != null) {
            // Send to specific user
            Notification n = new Notification(req.getReceiverUsername(), senderUsername, req.getTitle(),
                    req.getContent(), "MESSAGE", null);
            notificationRepo.save(n);
            count++;
        }

        return ResponseEntity.ok(Map.of("sent_count", count));
    }

    // DTO for request
    public static class SendNotificationRequest {
        private Long classId;
        private String receiverUsername;
        private String title;
        private String content;

        public Long getClassId() {
            return classId;
        }

        public void setClassId(Long classId) {
            this.classId = classId;
        }

        public String getReceiverUsername() {
            return receiverUsername;
        }

        public void setReceiverUsername(String receiverUsername) {
            this.receiverUsername = receiverUsername;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
