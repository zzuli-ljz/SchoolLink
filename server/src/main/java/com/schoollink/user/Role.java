package com.schoollink.user;

public enum Role {
    SYSTEM_ADMIN,
    SCHOOL_ADMIN,
    TEACHER,
    PARENT,
    STUDENT;

    public String homepagePath() {
        // 使用传统 switch 以兼容旧 JDK（避免箭头表达式导致运行环境报错）
        switch (this) {
            case SYSTEM_ADMIN:
                return "/home/system-admin.html";
            case SCHOOL_ADMIN:
                return "/home/school-admin.html";
            case TEACHER:
                return "/home/teacher.html";
            case PARENT:
                return "/home/parent.html";
            case STUDENT:
                return "/home/student.html";
            default:
                return "/";
        }
    }
}