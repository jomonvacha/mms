package com.roots.mms.controller;

import com.roots.mms.dto.request.UserPreferencesRequest;
import com.roots.mms.dto.response.UserPreferencesResponse;
import com.roots.mms.entity.UserPreferences;
import com.roots.mms.exception.AuthorizationException;
import com.roots.mms.repository.UserPreferencesRepository;
import com.roots.mms.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user/preferences")
@RequiredArgsConstructor
@Slf4j
public class UserPreferencesController {

    private final UserPreferencesRepository preferencesRepository;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserPreferencesResponse> getMyPreferences() {
        String userId = SecurityUtils.getCurrentUserIdOrNull();
        if (userId == null) throw new AuthorizationException("User", "read_prefs");
        return preferencesRepository.findById(java.util.UUID.fromString(userId))
                .map(up -> ResponseEntity.ok(toResponse(up)))
                .orElse(ResponseEntity.ok(UserPreferencesResponse.builder()
                        .theme("system").language("en").emailNotifications(true).navbarDisplay("avatar")
                        .notificationPrefs(com.roots.mms.service.NotificationPreferences.effective(null))
                        .build()));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserPreferencesResponse> setMyPreferences(@Valid @RequestBody UserPreferencesRequest req) {
        String userId = SecurityUtils.getCurrentUserIdOrNull();
        if (userId == null) throw new AuthorizationException("User", "write_prefs");
        java.util.UUID userUuid = java.util.UUID.fromString(userId);
        UserPreferences prefs = preferencesRepository.findById(userUuid).orElseGet(UserPreferences::new);
        prefs.setUserId(userUuid);
        prefs.setTheme(req.getTheme());
        prefs.setLanguage(req.getLanguage());
        if (req.getCountry() != null) prefs.setCountry(req.getCountry());
        if (req.getTimezone() != null) prefs.setTimezone(req.getTimezone());
        prefs.setEmailNotifications(req.isEmailNotifications());
        String nav = req.getNavbarDisplay();
        if (nav == null || !(nav.equals("avatar") || nav.equals("initials") || nav.equals("name"))) nav = "avatar";
        prefs.setNavbarDisplay(nav);
        if (req.getNotificationPrefs() != null) {
            prefs.setNotificationPrefs(
                    com.roots.mms.service.NotificationPreferences.sanitize(req.getNotificationPrefs()));
        }
        preferencesRepository.save(prefs);
        log.info("Updated preferences for userId={} theme={} lang={}", userId, req.getTheme(), req.getLanguage());
        return ResponseEntity.ok(toResponse(prefs));
    }

    private static UserPreferencesResponse toResponse(UserPreferences up) {
        return UserPreferencesResponse.builder()
                .theme(up.getTheme())
                .language(up.getLanguage())
                .country(up.getCountry())
                .timezone(up.getTimezone())
                .emailNotifications(Boolean.TRUE.equals(up.getEmailNotifications()))
                .navbarDisplay(up.getNavbarDisplay() != null ? up.getNavbarDisplay() : "avatar")
                .notificationPrefs(com.roots.mms.service.NotificationPreferences.effective(up.getNotificationPrefs()))
                .build();
    }
}
