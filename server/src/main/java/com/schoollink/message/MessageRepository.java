package com.schoollink.message;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByReceiverUsernameOrderByCreatedAtDesc(String receiverUsername);

    List<Message> findBySenderUsernameOrderByCreatedAtDesc(String senderUsername);

    // Find conversation between two users
    List<Message> findBySenderUsernameAndReceiverUsernameOrReceiverUsernameAndSenderUsernameOrderByCreatedAtDesc(
            String sender1, String receiver1, String receiver2, String sender2);
}
