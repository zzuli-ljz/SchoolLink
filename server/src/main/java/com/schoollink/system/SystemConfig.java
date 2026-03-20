package com.schoollink.system;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity
@Table(name = "system_config")
public class SystemConfig {
    @Id
    private Long id = 1L; // Singleton ID

    @Column(nullable = false)
    private String systemName = "家校通管理系统";

    private String logoUrl = "/assets/logo.png";

    private int maxAttachmentSizeMb = 10;

    private boolean moduleNoticeEnabled = true;
    private boolean moduleAssignmentEnabled = true;
    private boolean moduleAttendanceEnabled = true;

    public SystemConfig() {
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public int getMaxAttachmentSizeMb() {
        return maxAttachmentSizeMb;
    }

    public void setMaxAttachmentSizeMb(int maxAttachmentSizeMb) {
        this.maxAttachmentSizeMb = maxAttachmentSizeMb;
    }

    public boolean isModuleNoticeEnabled() {
        return moduleNoticeEnabled;
    }

    public void setModuleNoticeEnabled(boolean moduleNoticeEnabled) {
        this.moduleNoticeEnabled = moduleNoticeEnabled;
    }

    public boolean isModuleAssignmentEnabled() {
        return moduleAssignmentEnabled;
    }

    public void setModuleAssignmentEnabled(boolean moduleAssignmentEnabled) {
        this.moduleAssignmentEnabled = moduleAssignmentEnabled;
    }

    public boolean isModuleAttendanceEnabled() {
        return moduleAttendanceEnabled;
    }

    public void setModuleAttendanceEnabled(boolean moduleAttendanceEnabled) {
        this.moduleAttendanceEnabled = moduleAttendanceEnabled;
    }
}
