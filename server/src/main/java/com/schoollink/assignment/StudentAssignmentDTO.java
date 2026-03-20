package com.schoollink.assignment;

public class StudentAssignmentDTO {
    private Assignment assignment;
    private Submission submission;
    private String status; // UNFINISHED, PENDING_GRADING, COMPLETED, EXPIRED
    private String publisher;

    public StudentAssignmentDTO() {
    }

    public StudentAssignmentDTO(Assignment assignment, Submission submission, String status, String publisher) {
        this.assignment = assignment;
        this.submission = submission;
        this.status = status;
        this.publisher = publisher;
    }

    public Assignment getAssignment() {
        return assignment;
    }

    public void setAssignment(Assignment assignment) {
        this.assignment = assignment;
    }

    public Submission getSubmission() {
        return submission;
    }

    public void setSubmission(Submission submission) {
        this.submission = submission;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }
}
