package com.schoollink.invite;

import com.schoollink.user.Role;
import com.schoollink.school.SchoolClass;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "invite_codes")
public class InviteCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role roleAllowed; // 仅允许 PARENT 或 STUDENT

    @Column(nullable = false)
    private boolean active = true; // 单次使用后置为 false

    @Column(nullable = false, length = 64)
    private String createdBy; // 教师用户名

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id")
    @JsonIgnore
    private SchoolClass schoolClass; // 邀请码所属班级

    public InviteCode() {}

    public InviteCode(String code, Role roleAllowed, String createdBy) {
        this.code = code;
        this.roleAllowed = roleAllowed;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Role getRoleAllowed() { return roleAllowed; }
    public void setRoleAllowed(Role roleAllowed) { this.roleAllowed = roleAllowed; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public SchoolClass getSchoolClass() { return schoolClass; }
    public void setSchoolClass(SchoolClass schoolClass) { this.schoolClass = schoolClass; }
}