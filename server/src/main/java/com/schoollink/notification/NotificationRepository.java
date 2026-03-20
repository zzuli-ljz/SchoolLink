package com.schoollink.notification;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByReceiverUsernameOrderByCreatedAtDesc(String receiverUsername);

    List<Notification> findBySenderUsernameOrderByCreatedAtDesc(String senderUsername);
}
