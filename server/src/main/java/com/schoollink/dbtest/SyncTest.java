package com.schoollink.dbtest;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "app_test_sync")
public class SyncTest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String device;

    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getDevice() { return device; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }

    public void setId(Long id) { this.id = id; }
    public void setDevice(String device) { this.device = device; }
    public void setNote(String note) { this.note = note; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}