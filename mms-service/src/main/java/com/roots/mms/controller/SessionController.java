package com.roots.mms.controller;

import com.roots.mms.dto.response.MessageResponse;
import com.roots.mms.dto.response.SessionResponse;
import com.roots.mms.entity.UserSession;
import com.roots.mms.exception.AuthorizationException;
import com.roots.mms.security.SecurityUtils;
import com.roots.mms.security.jwt.JwtUtils;
import com.roots.mms.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Active-sessions management for the signed-in user (TradingView "Active sessions"
 * parity): list devices, revoke one, or sign out everywhere else.
 */
@RestController
@RequestMapping("/api/users/me/sessions")
@RequiredArgsConstructor
@Slf4j
public class SessionController {

    private final SessionService sessionService;
    private final JwtUtils jwtUtils;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SessionResponse>> listSessions(HttpServletRequest request) {
        String userId = requireUser();
        String currentSid = currentSessionId(request);
        List<SessionResponse> sessions = sessionService.listActive(userId).stream()
                .map(s -> toResponse(s, currentSid))
                .toList();
        return ResponseEntity.ok(sessions);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MessageResponse> revokeSession(@PathVariable String id) {
        String userId = requireUser();
        sessionService.revoke(userId, id);
        return ResponseEntity.ok(new MessageResponse("Session signed out"));
    }

    /** Sign out every other session, keeping the one that made this request. */
    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MessageResponse> revokeOthers(HttpServletRequest request) {
        String userId = requireUser();
        int revoked = sessionService.revokeAllExcept(userId, currentSessionId(request));
        return ResponseEntity.ok(new MessageResponse("Signed out " + revoked + " other session(s)"));
    }

    private static String requireUser() {
        String userId = SecurityUtils.getCurrentUserIdOrNull();
        if (userId == null) throw new AuthorizationException("Session", "manage");
        return userId;
    }

    private String currentSessionId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        return jwtUtils.getSessionId(header.substring(7));
    }

    private static SessionResponse toResponse(UserSession s, String currentSid) {
        return SessionResponse.builder()
                .id(s.getId().toString())
                .deviceLabel(s.getDeviceLabel())
                .userAgent(s.getUserAgent())
                .ip(s.getIp())
                .createdAt(s.getCreatedAt())
                .lastActiveAt(s.getLastActiveAt())
                .expiresAt(s.getExpiresAt())
                .current(currentSid != null && currentSid.equals(s.getId().toString()))
                .build();
    }
}
