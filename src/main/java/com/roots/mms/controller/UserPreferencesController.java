package com.roots.mms.controller;

import com.roots.mms.dto.request.UserPreferencesRequest;
import com.roots.mms.dto.response.UserPreferencesResponse;
import com.roots.mms.entity.User;
import com.roots.mms.entity.UserPreferences;
import com.roots.mms.exception.AuthorizationException;
import com.roots.mms.repository.UserPreferencesRepository;
import com.roots.mms.repository.UserRepository;
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

    private final UserRepository userRepository;
    private final UserPreferencesRepository preferencesRepository;

    @GetMapping
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN') or hasRole('MANAGER') or hasRole('MEMBER')")
    public ResponseEntity<UserPreferencesResponse> getMyPreferences() {
        Long userId = SecurityUtils.getCurrentUserIdOrNull();
        if (userId == null) throw new AuthorizationException("User", "read_prefs");
        return preferencesRepository.findById(userId)
                .map(up -> ResponseEntity.ok(toResponse(up)))
                .orElse(ResponseEntity.ok(UserPreferencesResponse.builder()
                        .theme("system").language("en").emailNotifications(true).navbarDisplay("avatar").build()));
    }

    @PostMapping
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN') or hasRole('MANAGER') or hasRole('MEMBER')")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<UserPreferencesResponse> setMyPreferences(@Valid @RequestBody UserPreferencesRequest req) {
        Long userId = SecurityUtils.getCurrentUserIdOrNull();
        if (userId == null) throw new AuthorizationException("User", "write_prefs");
        User user = userRepository.getReferenceById(userId);
        UserPreferences prefs = preferencesRepository.findById(userId).orElseGet(UserPreferences::new);
        prefs.setUser(user);
        prefs.setTheme(req.getTheme());
        prefs.setLanguage(req.getLanguage());
        prefs.setEmailNotifications(req.isEmailNotifications());
        // Validate navbarDisplay
        String nav = req.getNavbarDisplay();
        if (nav == null || !(nav.equals("avatar") || nav.equals("name"))) {
            nav = "avatar";
        }
        prefs.setNavbarDisplay(nav);
        preferencesRepository.save(prefs);
        log.info("Updated preferences for userId={} theme={} lang={}", userId, req.getTheme(), req.getLanguage());
        return ResponseEntity.ok(toResponse(prefs));
    }

    private static UserPreferencesResponse toResponse(UserPreferences up) {
        return UserPreferencesResponse.builder()
                .theme(up.getTheme())
                .language(up.getLanguage())
                .emailNotifications(Boolean.TRUE.equals(up.getEmailNotifications()))
                .navbarDisplay(up.getNavbarDisplay() == null ? "avatar" : up.getNavbarDisplay())
                .build();
    }
}
