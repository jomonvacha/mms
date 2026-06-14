package com.roots.mms.controller;

import com.roots.mms.service.email.LoggingEmailService;
import com.roots.mms.service.email.OutgoingEmail;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Dev-only endpoint that exposes emails captured by {@link LoggingEmailService}.
 * Only registered when the in-memory email store is active (i.e. {@code app.email.mode=log}).
 * Useful for automated tests of password-reset / email-verification flows.
 *
 * <p><strong>Not exposed in production.</strong> When you switch {@code app.email.mode=smtp},
 * the {@code LoggingEmailService} bean isn't created, so this controller's
 * {@code @ConditionalOnBean} also drops it.
 */
@RestController
@RequestMapping("/api/dev/mail")
@ConditionalOnBean(LoggingEmailService.class)
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class DevMailController {

    private final LoggingEmailService loggingEmailService;

    @GetMapping
    public ResponseEntity<List<OutgoingEmail>> recent() {
        return ResponseEntity.ok(loggingEmailService.recent());
    }

    @GetMapping("/last")
    public ResponseEntity<OutgoingEmail> lastTo(@RequestParam String to) {
        OutgoingEmail e = loggingEmailService.lastTo(to);
        if (e == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(e);
    }

    @DeleteMapping
    public ResponseEntity<Void> clear() {
        loggingEmailService.clear();
        return ResponseEntity.noContent().build();
    }
}
