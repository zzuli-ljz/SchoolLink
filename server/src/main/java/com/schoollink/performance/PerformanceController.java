package com.schoollink.performance;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.schoollink.auth.JwtService;

import io.jsonwebtoken.Claims;

@RestController
@RequestMapping("/api/performance")
public class PerformanceController {
    private final PerformanceRepository performanceRepo;
    private final JwtService jwtService;

    public PerformanceController(PerformanceRepository performanceRepo, JwtService jwtService) {
        this.performanceRepo = performanceRepo;
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

    @GetMapping
    public ResponseEntity<?> listPerformance(@RequestHeader("Authorization") String authorization,
            @RequestParam Long studentId) {
        if (studentId == null)
            return ResponseEntity.badRequest().body(Map.of("error", "studentId required"));
        return ResponseEntity.ok(performanceRepo.findByStudentIdOrderByDateDesc(studentId));
    }

    @PostMapping
    public ResponseEntity<?> addPerformance(@RequestHeader("Authorization") String authorization,
            @RequestBody Performance performance) {
        String role = roleFromAuth(authorization);
        if (role == null || (!role.equals("TEACHER") && !role.equals("SCHOOL_ADMIN") && !role.equals("SYSTEM_ADMIN"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }

        if (performance.getStudentId() == null || performance.getTitle() == null || performance.getScore() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing fields"));
        }

        Performance saved = performanceRepo.save(performance);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}
