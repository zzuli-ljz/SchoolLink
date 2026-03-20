package com.schoollink.auth;

public class AuthResponse {
    private String token;
    private String role;
    private String homepagePath;
    private String message;

    public AuthResponse() {}

    public AuthResponse(String token, String role, String homepagePath, String message) {
        this.token = token;
        this.role = role;
        this.homepagePath = homepagePath;
        this.message = message;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getHomepagePath() {
        return homepagePath;
    }

    public void setHomepagePath(String homepagePath) {
        this.homepagePath = homepagePath;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}