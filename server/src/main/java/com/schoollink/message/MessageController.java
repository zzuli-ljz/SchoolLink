package com.schoollink.message;

import com.schoollink.auth.JwtService;
import com.schoollink.user.User;
import com.schoollink.user.UserService;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private final MessageRepository messageRepo;
    private final JwtService jwtService;
    private final UserService userService;

    public MessageController(MessageRepository messageRepo, JwtService jwtService, UserService userService) {
        this.messageRepo = messageRepo;
        this.jwtService = jwtService;
        this.userService = userService;
    }

    private String usernameFromAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            return null;
        try {
            String token = authHeader.substring(7);
            Claims claims = jwtService.parse(token);
            return claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    @PostMapping
    public ResponseEntity<?> sendMessage(@RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, String> req) {
        String sender = usernameFromAuth(authorization);
        if (sender == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String receiver = req.get("receiver"); // Username
        String content = req.get("content");

        if (receiver == null || content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Receiver and content required"));
        }

        // Verify receiver exists
        if (userService.findByUsername(receiver).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Receiver not found"));
        }

        Message msg = new Message(sender, receiver, content);
        messageRepo.save(msg);
        return ResponseEntity.ok(msg);
    }

    @GetMapping
    public ResponseEntity<?> listMessages(@RequestHeader("Authorization") String authorization,
            @RequestParam(value = "with", required = false) String withUser) {
        String current = usernameFromAuth(authorization);
        if (current == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        if (withUser != null) {
            // Conversation view
            return ResponseEntity.ok(messageRepo
                    .findBySenderUsernameAndReceiverUsernameOrReceiverUsernameAndSenderUsernameOrderByCreatedAtDesc(
                            current, withUser, current, withUser));
        } else {
            // List all messages involved (received or sent) - simplified for now, or
            // grouped
            // For parent view, they usually want to see messages with the class teacher.
            // Let's just return all for now and let frontend filter or just return
            // "received" messages.
            // Actually, conversation view is better. If 'with' is not provided, maybe list
            // recent contacts?
            // For simplicity, let's return all received messages if 'with' is null.
            return ResponseEntity.ok(messageRepo.findByReceiverUsernameOrderByCreatedAtDesc(current));
        }
    }
}
