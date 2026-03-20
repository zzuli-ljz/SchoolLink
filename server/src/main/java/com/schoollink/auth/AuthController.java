package com.schoollink.auth;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.schoollink.invite.InviteCode;
import com.schoollink.invite.InviteCodeRepository;
import com.schoollink.school.SchoolClass;
import com.schoollink.school.Student;
import com.schoollink.school.StudentRepository;
import com.schoollink.user.Role;
import com.schoollink.user.User;
import com.schoollink.user.UserService;

import io.jsonwebtoken.Claims;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthController.class);
    private final UserService userService;
    private final JwtService jwtService;
    private final InviteCodeRepository inviteRepo;
    private final StudentRepository studentRepo;

    public AuthController(UserService userService, JwtService jwtService, InviteCodeRepository inviteRepo,
            StudentRepository studentRepo) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.inviteRepo = inviteRepo;
        this.studentRepo = studentRepo;
    }

    @GetMapping("/roles")
    public List<Role> roles() {
        return Arrays.asList(Role.values());
    }

    // 调试：原样回显登录请求体，排查反序列化是否异常
    @PostMapping("/login-debug")
    public ResponseEntity<?> loginDebug(@RequestBody java.util.Map<String, Object> body) {
        log.info("login-debug body keys: {}", body.keySet());
        return ResponseEntity.ok(body);
    }

    // 调试：直接生成并返回一个示例JWT，验证生成过程与序列化
    @GetMapping("/test-token")
    public ResponseEntity<String> testToken() {
        try {
            String token = jwtService.generateToken("school", Role.SCHOOL_ADMIN);
            return ResponseEntity.ok(token);
        } catch (Throwable t) {
            log.error("test-token failed", t);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("error:" + t.getClass().getSimpleName()
                            + (t.getMessage() != null ? (" - " + t.getMessage()) : ""));
        }
    }

    @GetMapping("/check-student")
    @Transactional
    public ResponseEntity<?> checkStudent(@RequestParam("inviteCode") String inviteCode,
            @RequestParam("studentName") String studentName) {
        try {
            log.info("check-student request: inviteCode={}, studentName={}", inviteCode, studentName);
            if (inviteCode == null || studentName == null) {
                return ResponseEntity.badRequest().body(java.util.Map.of("error", "缺少参数"));
            }
            Optional<InviteCode> opt = inviteRepo.findByCode(inviteCode.trim());
            if (opt.isEmpty()) {
                log.warn("check-student: invite code not found");
                return ResponseEntity.badRequest().body(java.util.Map.of("error", "邀请码无效"));
            }
            SchoolClass cls = opt.get().getSchoolClass();
            if (cls == null) {
                log.warn("check-student: invite code has no class");
                return ResponseEntity.badRequest().body(java.util.Map.of("error", "邀请码未绑定班级"));
            }
            List<Student> students = studentRepo.findBySchoolClass_Id(cls.getId());
            log.info("check-student: classId={}, student count={}, names={}", cls.getId(), students.size(),
                    students.stream().map(Student::getName).toList());

            Optional<Student> match = students.stream()
                    .filter(s -> s.getName() != null && s.getName().equals(studentName.trim()))
                    .findFirst();

            if (match.isPresent()) {
                Student s = match.get();
                // 返回学生基本信息供确认
                java.util.Map<String, Object> data = new java.util.HashMap<>();
                data.put("exists", true);
                data.put("studentName", s.getName());
                data.put("studentNo", s.getStudentNo());
                data.put("isBound", s.getParentUser() != null);
                return ResponseEntity.ok(data);
            } else {
                log.warn("check-student: no match found for name={}", studentName);
                return ResponseEntity.badRequest().body(java.util.Map.of("error", "student_not_found"));
            }
        } catch (Exception e) {
            log.error("check-student error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("error", "internal_error", "message", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest req) {
        try {
            log.info("Login attempt: username={}, role(raw)={}", req.getUsername(), req.getRole());
            // 将字符串角色解析为枚举（兼容中文与英文枚举名）
            Role chosenRole = parseRole(req.getRole());
            if (chosenRole == null) {
                log.warn("Login failed: invalid role value={}", req.getRole());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new AuthResponse(null, null, null, "角色无效，请重新选择"));
            }
            java.util.Optional<User> userOpt = userService.findByUsername(req.getUsername());
            if (userOpt.isEmpty()) {
                log.warn("Login failed: user not found or password mismatch for username={}", req.getUsername());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new AuthResponse(null, null, null, "用户名或密码错误"));
            }

            User user = userOpt.get();
            if (!user.isEnabled()) {
                log.warn("Login failed: account disabled for username={}", req.getUsername());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new AuthResponse(null, null, null, "账号已被禁用，请联系管理员"));
            }
            if (!user.getPassword().equals(req.getPassword())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new AuthResponse(null, null, null, "用户名或密码错误"));
            }

            if (req.getRole() == null || req.getRole().isBlank()) {
                log.warn("Login failed: role not selected");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new AuthResponse(null, null, null, "必须选择角色登录"));
            }

            if (user.getRole() != chosenRole) {
                String msg = "该账户绑定角色为 " + user.getRole() + "，不可按 " + chosenRole + " 登录";
                log.warn("Login failed: role mismatch. bound={}, chosen={}", user.getRole(), chosenRole);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new AuthResponse(null, null, null, msg));
            }

            String homepage = user.getRole().homepagePath();
            String token = jwtService.generateToken(user.getUsername(), user.getRole());
            AuthResponse resp = new AuthResponse(token, user.getRole().name(), homepage, "登录成功");
            log.info("Login success: username={}, role(resolved)={}, homepage={}", req.getUsername(), user.getRole(),
                    homepage);
            return ResponseEntity.ok(resp);
        } catch (Throwable e) {
            log.error("Login failed due to unexpected error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AuthResponse(null, null, null, "服务器内部错误：" + e.getClass().getSimpleName()
                            + (e.getMessage() != null ? (" - " + e.getMessage()) : "")));
        }
    }

    // 兼容解析登录页传来的角色字符串
    private Role parseRole(String val) {
        if (val == null)
            return null;
        String s = val.trim().toUpperCase();
        // 先匹配中文标签
        switch (val.trim()) {
            case "系统管理员":
                return Role.SYSTEM_ADMIN;
            case "学校管理员":
                return Role.SCHOOL_ADMIN;
            case "老师":
                return Role.TEACHER;
            case "家长":
                return Role.PARENT;
            case "学生":
                return Role.STUDENT;
            default:
                break;
        }
        // 再匹配英文枚举名
        try {
            return Role.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @GetMapping("/ping")
    public ResponseEntity<?> ping() {
        return ResponseEntity.ok(new AuthResponse(null, null, "/", "pong"));
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verify(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(null, null, null, "缺少令牌"));
        }
        String token = auth.substring(7);
        try {
            Claims claims = jwtService.parse(token);
            String username = claims.getSubject();
            String roleName = (String) claims.get("role");
            Role role;
            try {
                role = Role.valueOf(roleName);
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new AuthResponse(null, null, null, "令牌无效：角色错误"));
            }
            String homepage = role.homepagePath();
            return ResponseEntity.ok(new AuthResponse(token, role.name(), homepage, "令牌有效: " + username));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(null, null, null, "令牌无效"));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        try {
            String password = req.getPassword() == null ? "" : req.getPassword();
            String roleRaw = req.getRole();
            String inviteCode = req.getInviteCode() == null ? "" : req.getInviteCode().trim();

            if (password.isEmpty() || roleRaw == null || roleRaw.isBlank() || inviteCode.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new AuthResponse(null, null, null, "缺少必要字段"));
            }

            Role chosenRole = parseRole(roleRaw);
            if (chosenRole == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new AuthResponse(null, null, null, "角色无效"));
            }
            if (!(chosenRole == Role.PARENT || chosenRole == Role.STUDENT)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new AuthResponse(null, null, null, "该角色不支持自助注册"));
            }

            // 按角色生成用户名并校验必填项
            String username;
            if (chosenRole == Role.PARENT) {
                String studentName = req.getStudentName() == null ? "" : req.getStudentName().trim();
                String relationship = req.getRelationship() == null ? "" : req.getRelationship().trim();
                if (studentName.isEmpty() || relationship.isEmpty()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(new AuthResponse(null, null, null, "家长注册需填写学生姓名与关系"));
                }
                username = studentName + relationship;
            } else { // STUDENT
                String studentName = req.getStudentName() == null ? "" : req.getStudentName().trim();
                String studentNo = req.getStudentNo() == null ? "" : req.getStudentNo().trim();
                if (studentName.isEmpty() || studentNo.isEmpty()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(new AuthResponse(null, null, null, "学生注册需填写姓名与学号"));
                }
                username = studentName;
            }

            if (userService.existsByUsername(username)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new AuthResponse(null, null, null, "用户名已存在"));
            }

            Optional<InviteCode> opt = inviteRepo.findByCode(inviteCode);
            if (opt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new AuthResponse(null, null, null, "邀请码不存在"));
            }
            InviteCode code = opt.get();
            if (code.getRoleAllowed() != chosenRole) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new AuthResponse(null, null, null, "邀请码角色不匹配"));
            }
            SchoolClass targetClass = code.getSchoolClass();
            if (targetClass == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new AuthResponse(null, null, null, "邀请码未绑定班级"));
            }

            User newUser = new User(username, password, chosenRole);
            newUser.setClassJoined(targetClass);
            if (chosenRole == Role.PARENT && req.getPhoneNumber() != null) {
                newUser.setPhoneNumber(req.getPhoneNumber().trim());
            }
            User saved = userService.save(newUser);

            // 家长注册时：绑定学生
            if (chosenRole == Role.PARENT) {
                // 再次查找学生以确保安全绑定
                String sName = req.getStudentName().trim();
                List<Student> all = studentRepo.findBySchoolClass_Id(targetClass.getId());
                Optional<Student> match = all.stream().filter(s -> s.getName().equals(sName)).findFirst();
                if (match.isPresent()) {
                    Student st = match.get();

                    // 双向绑定：设置 User 的 boundStudentId
                    saved.setBoundStudentId(st.getId());
                    userService.save(saved);

                    if (st.getParentUser() == null) {
                        st.setParentUser(saved);
                        studentRepo.save(st);
                    } else {
                        // 如果已被绑定，此处策略：
                        // 1. 报错回滚？(有点复杂)
                        // 2. 忽略绑定？(用户已创建但未绑定)
                        // 根据需求"绑定后不可修改"，如果已被绑定，则新用户无法绑定该学生。
                        // 前端 check-student 应该已经提示了。这里静默失败或者记录日志即可。
                        log.warn("Student {} already bound, skipping bind for user {}", st.getId(),
                                saved.getUsername());
                    }
                }
            }

            // 保持邀请码长期有效（固定码），不失效

            // 学生注册时，自动创建学生记录并加入对应班级，保存学号
            if (chosenRole == Role.STUDENT) {
                // 检查是否已有同名同号的学生记录（老师导入的）
                Optional<Student> existing = studentRepo.findBySchoolClass_IdAndNameAndStudentNo(targetClass.getId(),
                        username, req.getStudentNo());
                if (existing.isPresent()) {
                    Student st = existing.get();
                    st.setUsername(username);
                    studentRepo.save(st);
                } else {
                    Student st = new Student();
                    st.setName(username);
                    st.setStudentNo(req.getStudentNo());
                    st.setSchoolClass(targetClass);
                    st.setUsername(username);
                    studentRepo.save(st);
                }
            }

            String token = jwtService.generateToken(saved.getUsername(), saved.getRole());
            String homepage = saved.getRole().homepagePath();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new AuthResponse(token, saved.getRole().name(), homepage, "注册成功，已自动登录"));
        } catch (Exception e) {
            log.error("register failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AuthResponse(null, null, null, "服务器错误"));
        }
    }
}