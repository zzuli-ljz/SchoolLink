package com.schoollink.auth;

public class RegisterRequest {
    private String username; // 前端将不再直接填写，后端会按角色生成
    private String password;
    private String role; // 仅允许 PARENT / STUDENT
    private String inviteCode;
    // 新增：用于按角色构造用户名与校验字段
    private String studentName; // 学生姓名（家长/学生均需）
    private String studentNo; // 学号（仅学生）
    private String relationship; // 与学生关系（仅家长）：父亲/母亲/其他
    private String phoneNumber; // 联系电话（仅家长）

    public RegisterRequest() {
    }

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

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getStudentNo() {
        return studentNo;
    }

    public void setStudentNo(String studentNo) {
        this.studentNo = studentNo;
    }

    public String getRelationship() {
        return relationship;
    }

    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}