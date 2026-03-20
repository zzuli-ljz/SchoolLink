package com.schoollink.grade;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "exam_results", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"examId", "studentId", "subject"})
})
public class ExamResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long examId;

    @Column(nullable = false)
    private Long studentId;
    
    private String studentName; // Cached for convenience

    @Column(nullable = false)
    private String subject;

    private Double score; // Nullable for absent/missing

    private Long classId; // Cached for aggregation

    private Long teacherId; // Uploader

    @Column(nullable = false)
    private boolean published = false;

    private LocalDateTime createdAt;

    public ExamResult() {
        this.createdAt = LocalDateTime.now();
    }

    public ExamResult(Long examId, Long studentId, String studentName, String subject, Double score, Long classId, Long teacherId) {
        this();
        this.examId = examId;
        this.studentId = studentId;
        this.studentName = studentName;
        this.subject = subject;
        this.score = score;
        this.classId = classId;
        this.teacherId = teacherId;
        this.published = false;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getExamId() { return examId; }
    public void setExamId(Long examId) { this.examId = examId; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
