package com.schoollink.auth;

public class AuthRequest {
    private String username;
    private String password;
    private String role; // 用户在登录页选择的角色（允许英文枚举名或中文标签）

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}