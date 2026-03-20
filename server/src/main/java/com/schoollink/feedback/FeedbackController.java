package com.schoollink.feedback;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.schoollink.auth.JwtService;

import io.jsonwebtoken.Claims;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackRepository repo;
    private final JwtService jwtService;

    public FeedbackController(FeedbackRepository repo, JwtService jwtService) {
        this.repo = repo;
        this.jwtService = jwtService;
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

    private String usernameFromAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            return "Anonymous";
        try {
            String token = authHeader.substring(7);
            Claims claims = jwtService.parse(token);
            return claims.getSubject();
        } catch (Exception e) {
            return "Anonymous";
        }
    }

    private boolean isSchoolAdmin(String role) {
        return "SCHOOL_ADMIN".equals(role) || "SYSTEM_ADMIN".equals(role);
    }

    @GetMapping
    public ResponseEntity<?> listFeedback(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String role = roleFromAuth(authorization);
        String username = usernameFromAuth(authorization);

        if (isSchoolAdmin(role)) {
            return ResponseEntity.ok(repo.findAllByOrderByCreatedAtDesc());
        } else {
            return ResponseEntity.ok(repo.findBySenderUsernameOrderByCreatedAtDesc(username));
        }
    }

    @PostMapping
    public ResponseEntity<?> submitFeedback(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "content_required"));
        }

        String username = usernameFromAuth(authorization);
        String role = roleFromAuth(authorization);

        Feedback fb = new Feedback(content, username, role);
        repo.save(fb);

        return ResponseEntity.status(HttpStatus.CREATED).body(fb);
    }
}
