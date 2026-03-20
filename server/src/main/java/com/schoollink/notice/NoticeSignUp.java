package com.schoollink.notice;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "notice_signups", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "noticeId", "userId" })
})
public class NoticeSignUp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long noticeId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private OffsetDateTime signedUpAt = OffsetDateTime.now();

    public NoticeSignUp() {
    }

    public NoticeSignUp(Long noticeId, Long userId) {
        this.noticeId = noticeId;
        this.userId = userId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getNoticeId() {
        return noticeId;
    }

    public void setNoticeId(Long noticeId) {
        this.noticeId = noticeId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public OffsetDateTime getSignedUpAt() {
        return signedUpAt;
    }

    public void setSignedUpAt(OffsetDateTime signedUpAt) {
        this.signedUpAt = signedUpAt;
    }
}
