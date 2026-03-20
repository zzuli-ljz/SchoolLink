package com.schoollink.assignment;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "submissions")
public class Submission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long assignmentId;

    @Column(nullable = false)
    private Long studentId;

    @NotBlank
    @Size(max = 4000)
    @Column(nullable = false, length = 4000)
    private String content;

    @Column(nullable = false)
    private OffsetDateTime submittedAt = OffsetDateTime.now();

    // NEW FIELDS
    @Column(length = 20)
    private String status = "PENDING"; // PENDING, GRADED

    private Integer score; // 0-100

    @Size(max = 1000)
    @Column(length = 1000)
    private String feedback;

    private java.time.LocalDate extendedDueDate; // Allow re-submission until this date if RETURNED

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(Long assignmentId) {
        this.assignmentId = assignmentId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(OffsetDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public java.time.LocalDate getExtendedDueDate() {
        return extendedDueDate;
    }

    public void setExtendedDueDate(java.time.LocalDate extendedDueDate) {
        this.extendedDueDate = extendedDueDate;
    }
}