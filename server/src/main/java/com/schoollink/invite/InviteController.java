package com.schoollink.invite;

import com.schoollink.auth.JwtService;
import com.schoollink.school.SchoolClass;
import com.schoollink.school.SchoolClassRepository;
import com.schoollink.user.Role;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.List;

@RestController
@RequestMapping("/api/invites")
public class InviteController {
    private final InviteCodeRepository repo;
    private final JwtService jwtService;
    private final SchoolClassRepository classRepo;

    public InviteController(InviteCodeRepository repo, JwtService jwtService, SchoolClassRepository classRepo) {
        this.repo = repo;
        this.jwtService = jwtService;
        this.classRepo = classRepo;
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

    private boolean isTeacherOrAdmin(String role) {
        return role != null && ("TEACHER".equals(role) || "SCHOOL_ADMIN".equals(role) || "SYSTEM_ADMIN".equals(role));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, String> body) {
        String role = roleFromAuth(authorization);
        if (!isTeacherOrAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        String targetRole = body.get("role");
        String classIdRaw = body.get("classId");
        Role allowed;
        try {
            allowed = Role.valueOf(targetRole);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "role_invalid"));
        }
        if (!(allowed == Role.PARENT || allowed == Role.STUDENT)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "only_parent_or_student"));
        }
        if (classIdRaw == null || classIdRaw.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "classId_required"));
        }
        Long classId;
        try {
            classId = Long.parseLong(classIdRaw);
        } catch (NumberFormatException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "classId_invalid"));
        }
        Optional<SchoolClass> clsOpt = classRepo.findById(classId);
        if (clsOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "class_not_found"));
        }
        String creator;
        try {
            Claims claims = jwtService.parse(authorization.substring(7));
            creator = claims.getSubject();
        } catch (Exception ex) {
            creator = "unknown";
        }
        String code = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        InviteCode invite = new InviteCode(code, allowed, creator);
        invite.setSchoolClass(clsOpt.get());
        repo.save(invite);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("code", code, "role", allowed.name(), "classId", classId));
    }

    @GetMapping
    public ResponseEntity<?> listMine(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String role = roleFromAuth(authorization);
        if (!isTeacherOrAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        String creator;
        try {
            Claims claims = jwtService.parse(authorization.substring(7));
            creator = claims.getSubject();
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid_token"));
        }
        return ResponseEntity.ok(repo.findByCreatedByOrderByCreatedAtDesc(creator));
    }

    @GetMapping("/by-class/{classId}")
    public ResponseEntity<?> listByClass(@RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId) {
        String role = roleFromAuth(authorization);
        if (!isTeacherOrAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        // 验证班级存在
        Optional<SchoolClass> clsOpt = classRepo.findById(classId);
        if (clsOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "class_not_found"));
        }
        // 查询该班级的所有邀请码（不按创建者过滤），确保每班至少有家长/学生两个固定码
        List<InviteCode> codes = repo.findBySchoolClass_IdOrderByCreatedAtAsc(classId);
        // 仅保留家长/学生两个角色的码
        List<InviteCode> filtered = new java.util.ArrayList<>();
        for (InviteCode ic : codes) {
            if (ic.getRoleAllowed() == com.schoollink.user.Role.PARENT
                    || ic.getRoleAllowed() == com.schoollink.user.Role.STUDENT) {
                filtered.add(ic);
            }
        }
        boolean hasParent = filtered.stream().anyMatch(i -> i.getRoleAllowed() == com.schoollink.user.Role.PARENT);
        boolean hasStudent = filtered.stream().anyMatch(i -> i.getRoleAllowed() == com.schoollink.user.Role.STUDENT);
        // 如果缺失则自动补齐固定码
        String creator;
        try {
            Claims claims = jwtService.parse(authorization.substring(7));
            creator = claims.getSubject();
        } catch (Exception ex) {
            creator = "unknown";
        }
        if (!hasParent) {
            String parentCode = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
            InviteCode icParent = new InviteCode(parentCode, com.schoollink.user.Role.PARENT, creator);
            icParent.setSchoolClass(clsOpt.get());
            repo.save(icParent);
            filtered.add(icParent);
        }
        if (!hasStudent) {
            String studentCode = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
            InviteCode icStudent = new InviteCode(studentCode, com.schoollink.user.Role.STUDENT, creator);
            icStudent.setSchoolClass(clsOpt.get());
            repo.save(icStudent);
            filtered.add(icStudent);
        }
        // 返回当前班级的家长/学生固定码列表（避免直接序列化实体导致懒加载/循环问题）
        // 且进行去重，每个角色只返回一个（优先返回长度较短的自定义码，或者第一个）
        java.util.List<java.util.Map<String, Object>> dto = new java.util.ArrayList<>();
        java.util.Set<com.schoollink.user.Role> addedRoles = new java.util.HashSet<>();

        // 排序：优先展示看起来像自定义的码（比如长度短的，或者特定的），这里简单按长度排序，短的在前
        filtered.sort((a, b) -> Integer.compare(a.getCode().length(), b.getCode().length()));

        for (InviteCode ic : filtered) {
            if (!addedRoles.contains(ic.getRoleAllowed())) {
                dto.add(java.util.Map.of(
                        "code", ic.getCode(),
                        "roleAllowed", ic.getRoleAllowed().name(),
                        "active", ic.isActive()));
                addedRoles.add(ic.getRoleAllowed());
            }
        }
        return ResponseEntity.ok(dto);
    }
}