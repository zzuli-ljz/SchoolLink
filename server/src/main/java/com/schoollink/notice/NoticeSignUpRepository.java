package com.schoollink.notice;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface NoticeSignUpRepository extends JpaRepository<NoticeSignUp, Long> {
    List<NoticeSignUp> findByNoticeId(Long noticeId);

    Optional<NoticeSignUp> findByNoticeIdAndUserId(Long noticeId, Long userId);

    void deleteByNoticeId(Long noticeId);
}
