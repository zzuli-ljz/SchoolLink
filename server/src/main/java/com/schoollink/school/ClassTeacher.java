package com.schoollink.school;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.schoollink.user.User;

import jakarta.persistence.*;

@Entity
@Table(name = "class_teachers")
public class ClassTeacher {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "class_id", nullable = false)
    @JsonIgnore
    private SchoolClass schoolClass;

    @ManyToOne
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    @Column(nullable = false)
    private String subject;

    public ClassTeacher() {}

    public ClassTeacher(SchoolClass schoolClass, User teacher, String subject) {
        this.schoolClass = schoolClass;
        this.teacher = teacher;
        this.subject = subject;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SchoolClass getSchoolClass() {
        return schoolClass;
    }

    public void setSchoolClass(SchoolClass schoolClass) {
        this.schoolClass = schoolClass;
    }

    public User getTeacher() {
        return teacher;
    }

    public void setTeacher(User teacher) {
        this.teacher = teacher;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }
}
