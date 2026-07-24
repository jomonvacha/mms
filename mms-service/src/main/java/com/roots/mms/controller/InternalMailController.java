package com.roots.mms.controller;

import com.roots.mms.entity.User;
import com.roots.mms.repository.UserRepository;
import com.roots.mms.service.email.EmailService;
import com.roots.mms.service.email.OutgoingEmail;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Service-to-service mail dispatch so sibling apps (e.g. TradeCue) can send a user-facing email
 * through MMS's mail infrastructure without holding SMTP config or the user's address. Guarded by a
 * shared {@code X-Service-Token} compared against {@code app.internal.service-token}; when that token
 * is blank the endpoint is effectively disabled (every call is rejected). Never user-authenticated.
 */
@RestController
@RequestMapping("/api/internal/notifications")
public class InternalMailController {

    private static final Logger log = LoggerFactory.getLogger(InternalMailController.class);

    private final UserRepository users;
    private final EmailService email;
    private final String serviceToken;

    public InternalMailController(UserRepository users, EmailService email,
                                  @Value("${app.internal.service-token:}") String serviceToken) {
        this.users = users;
        this.email = email;
        this.serviceToken = serviceToken;
    }

    public record EmailRequest(@NotBlank String userId, @NotBlank String subject, @NotBlank String body) {}

    @PostMapping("/email")
    public ResponseEntity<Void> sendEmail(@RequestHeader(value = "X-Service-Token", required = false) String token,
                                          @RequestBody EmailRequest req) {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Internal mail dispatch is not enabled");
        }
        if (token == null || !serviceToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid service token");
        }
        UUID userId;
        try {
            userId = UUID.fromString(req.userId());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid userId");
        }
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "User has no email address");
        }
        email.send(OutgoingEmail.of(user.getEmail(), req.subject(), req.body()));
        log.info("Internal mail dispatched to user {} (subject: {})", userId, req.subject());
        return ResponseEntity.accepted().build();
    }
}
