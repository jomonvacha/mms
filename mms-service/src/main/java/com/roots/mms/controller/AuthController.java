package com.roots.mms.controller;

import com.roots.mms.dto.request.ForgotPasswordRequest;
import com.roots.mms.dto.request.GoogleIdTokenRequest;
import com.roots.mms.dto.request.LoginRequest;
import com.roots.mms.dto.request.ResetPasswordRequest;
import com.roots.mms.dto.request.SignupRequest;
import com.roots.mms.dto.response.JwtResponse;
import com.roots.mms.dto.response.MessageResponse;
import com.roots.mms.entity.AuthProvider;
import com.roots.mms.entity.ERole;
import com.roots.mms.entity.Role;
import com.roots.mms.entity.User;
import com.roots.mms.exception.AuthenticationException;
import com.roots.mms.exception.DuplicateResourceException;
import com.roots.mms.exception.ResourceNotFoundException;
import com.roots.mms.repository.RoleRepository;
import com.roots.mms.repository.UserRepository;
import com.roots.mms.security.jwt.JwtUtils;
import com.roots.mms.security.jwt.TokenBlacklistService;
import com.roots.mms.security.services.UserPrincipal;
import com.roots.mms.service.AppSettingService;
import com.roots.mms.service.MemberService;
import com.roots.mms.service.SignupInviteCodeService;
import com.roots.mms.service.TwoFactorService;
import com.roots.mms.service.VerificationTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthenticationManager authenticationManager;
  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder encoder;
  private final JwtUtils jwtUtils;
  private final TokenBlacklistService tokenBlacklist;
  private final VerificationTokenService verificationTokenService;
  private final TwoFactorService twoFactorService;
  private final MemberService memberService;
  private final SignupInviteCodeService inviteCodeService;
  private final AppSettingService appSettingService;
  @Value("${spring.security.oauth2.client.registration.google.client-id:}")
  private String googleClientId;

  @PostMapping("/signin")
  public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
    log.info("Authentication attempt for user: {}", loginRequest.getUsername());

    try {
      Authentication authentication = authenticationManager
        .authenticate(new UsernamePasswordAuthenticationToken(
          loginRequest.getUsername(),
          loginRequest.getPassword()));

      UserPrincipal userDetails = (UserPrincipal) authentication.getPrincipal();

      // 2FA gate: if the user has 2FA enabled, the client must supply a valid
      // TOTP (or recovery) code as part of the same signin request. We can't
      // stash a half-authenticated state because the rest of the API is stateless.
      User userEntity = userRepository.findById(java.util.UUID.fromString(userDetails.getId())).orElse(null);
      if (userEntity != null && Boolean.TRUE.equals(userEntity.getTotpEnabled())) {
        String code = loginRequest.getTotpCode();
        if (code == null || code.isBlank()) {
          throw new AuthenticationException("TWO_FACTOR_REQUIRED");
        }
        if (!twoFactorService.verifyDuringSignin(userEntity, code)) {
          log.warn("Invalid 2FA code for user: {}", loginRequest.getUsername());
          throw new AuthenticationException("Invalid authentication code");
        }
      }

      SecurityContextHolder.getContext().setAuthentication(authentication);
      String jwt = jwtUtils.generateJwtToken(authentication);
      String refreshToken = jwtUtils.generateRefreshToken(authentication);

      List<String> roles = userDetails.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toList());

      log.info("Successful authentication for user: {}", loginRequest.getUsername());

      return ResponseEntity.ok(new JwtResponse(jwt, refreshToken,
        userDetails.getId(),
        userDetails.getUsername(),
        userDetails.getEmail(),
        roles));
    } catch (BadCredentialsException e) {
      log.warn("Failed authentication attempt for user: {}", loginRequest.getUsername());
      throw new AuthenticationException("Invalid username or password");
    }
  }

  /**
   * Public pre-check so the signup page can validate the invite code before the
   * user fills the rest of the form. Does NOT consume the code.
   */
  @PostMapping("/validate-invite")
  public ResponseEntity<?> validateInvite(@RequestBody java.util.Map<String, String> body) {
    String code = body.get("code");
    var invite = inviteCodeService.requireRedeemable(code);
    return ResponseEntity.ok(java.util.Map.of(
            "valid", true,
            "description", invite.getDescription() == null ? "" : invite.getDescription()
    ));
  }

  /**
   * Public signup configuration, read by the IDFY signup form at mount. Exposes
   * only non-sensitive toggles that affect the form's shape (e.g. whether the
   * invite-code field should be shown).
   */
  @GetMapping("/signup-config")
  public ResponseEntity<?> signupConfig() {
    boolean inviteRequired = appSettingService.getBoolean(AppSettingService.SIGNUP_INVITE_REQUIRED, true);
    return ResponseEntity.ok(java.util.Map.of("inviteRequired", inviteRequired));
  }

  @PostMapping("/signup")
  public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
    log.info("User registration attempt for username: {}", signUpRequest.getUsername());

    // Validate invite up front so we don't create a user if the code is bad.
    // Honor the admin-controlled toggle: when off, invite codes are optional.
    boolean inviteRequired = appSettingService.getBoolean(AppSettingService.SIGNUP_INVITE_REQUIRED, true);
    boolean hasCode = signUpRequest.getInviteCode() != null && !signUpRequest.getInviteCode().isBlank();
    if (inviteRequired || hasCode) {
      inviteCodeService.requireRedeemable(signUpRequest.getInviteCode());
    }

    if (userRepository.existsByUsername(signUpRequest.getUsername())) {
      throw new DuplicateResourceException("User", "username", signUpRequest.getUsername());
    }

    if (userRepository.existsByEmail(signUpRequest.getEmail())) {
      throw new DuplicateResourceException("User", "email", signUpRequest.getEmail());
    }

    // Create new user's account
    User user = new User(signUpRequest.getUsername(),
      signUpRequest.getEmail(),
      encoder.encode(signUpRequest.getPassword()),
      signUpRequest.getFirstName(),
      signUpRequest.getLastName());

    user.setPhoneNumber(signUpRequest.getPhoneNumber());

    Set<Role> roles = new HashSet<>();

    Role memberRole = roleRepository.findByName(ERole.ROLE_MEMBER.name())
      .orElseThrow(() -> new ResourceNotFoundException("Role", "name", ERole.ROLE_MEMBER));
    roles.add(memberRole);

    user.setRoles(roles);
    User saved = userRepository.save(user);

    // Consume the invite code when one was supplied. When signup is open
    // (no code required), nothing to consume.
    if (hasCode) {
      try { inviteCodeService.consume(signUpRequest.getInviteCode()); } catch (Exception e) {
        log.warn("Failed to consume invite code for {}: {}", saved.getUsername(), e.getMessage());
      }
    }

    // Auto-assign BASIC membership
    try { memberService.ensureDefaultMembership(saved); } catch (Exception e) {
      log.warn("Failed to assign default membership for {}: {}", saved.getUsername(), e.getMessage());
    }

    // Fire-and-forget: send email verification link. Never blocks signup.
    try {
      verificationTokenService.startEmailVerification(saved);
    } catch (Exception e) {
      log.warn("Failed to dispatch verification email for {}: {}", saved.getEmail(), e.getMessage());
    }

    log.info("Successfully registered user: {}", signUpRequest.getUsername());

    return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
  }

  /**
   * Always returns 200. Intentionally does not disclose whether the email exists
   * (no user enumeration). When the email IS registered, a password-reset link is
   * sent via {@link VerificationTokenService}.
   */
  @PostMapping("/forgot-password")
  public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
    log.info("Password reset requested for email: {}", req.getEmail());
    verificationTokenService.startPasswordReset(req.getEmail());
    return ResponseEntity.ok(new MessageResponse(
            "If that email is registered, a reset link has been sent."));
  }

  /**
   * Consumes a password-reset token and sets the new password. Returns 400 if
   * the token is unknown, expired, already used, or the new password is weak.
   */
  @PostMapping("/reset-password")
  public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
    verificationTokenService.redeemPasswordReset(req.getToken(), req.getNewPassword());
    return ResponseEntity.ok(new MessageResponse("Password has been reset."));
  }

  /**
   * Consumes an email-verification token and marks the user as verified.
   * GET-friendly so clicking the link in an email plain-text client works.
   */
  @GetMapping("/verify-email")
  public ResponseEntity<?> verifyEmail(@RequestParam String token) {
    verificationTokenService.redeemEmailVerification(token);
    return ResponseEntity.ok(new MessageResponse("Email verified."));
  }

  /**
   * Stateless JWT signout: revoke the bearer access token (and optional refresh token)
   * by adding them to the in-memory blacklist until their natural expiry. The frontend
   * also clears its local storage. Idempotent — always returns 200.
   */
  @PostMapping("/signout")
  public ResponseEntity<?> signout(HttpServletRequest request,
                                   @RequestParam(required = false) String refreshToken) {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String accessToken = header.substring(7);
      try {
        if (jwtUtils.validateJwtToken(accessToken)) {
          tokenBlacklist.revoke(accessToken, jwtUtils.getExpirationEpochMs(accessToken));
        }
      } catch (Exception e) {
        log.debug("Could not revoke access token on signout: {}", e.getMessage());
      }
    }
    if (refreshToken != null && !refreshToken.isBlank()) {
      try {
        if (jwtUtils.validateJwtToken(refreshToken)) {
          tokenBlacklist.revoke(refreshToken, jwtUtils.getExpirationEpochMs(refreshToken));
        }
      } catch (Exception e) {
        log.debug("Could not revoke refresh token on signout: {}", e.getMessage());
      }
    }
    SecurityContextHolder.clearContext();
    return ResponseEntity.ok(new MessageResponse("Signed out"));
  }

  @PostMapping("/refresh")
  public ResponseEntity<?> refreshToken(@RequestParam String refreshToken) {
    // Reject refresh tokens that have been revoked via /signout, even if they're
    // still cryptographically valid (the blacklist covers them until their own exp).
    if (tokenBlacklist.isRevoked(refreshToken)) {
      throw new AuthenticationException("Refresh token has been revoked");
    }
    if (jwtUtils.validateJwtToken(refreshToken)) {
      String username = jwtUtils.getUserNameFromJwtToken(refreshToken);
      User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));

      String newJwt = jwtUtils.generateTokenFromUsername(user.getUsername());
      String newRefreshToken = jwtUtils.generateRefreshTokenFromUsername(user.getUsername());

      List<String> roles = user.getRoles().stream()
        .map(r -> r.getName())
        .collect(Collectors.toList());

      return ResponseEntity.ok(new JwtResponse(newJwt, newRefreshToken,
        user.getId() != null ? user.getId().toString() : null,
        user.getUsername(),
        user.getEmail(),
        roles));
    } else {
      throw new AuthenticationException("Invalid refresh token");
    }
  }

  @PostMapping("/google-id-token")
  public ResponseEntity<?> exchangeGoogleIdToken(@Valid @RequestBody GoogleIdTokenRequest req) {
    try {
      var httpTransport = new com.google.api.client.http.javanet.NetHttpTransport();
      var jsonFactory = com.google.api.client.json.gson.GsonFactory.getDefaultInstance();
      var verifier = new com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier.Builder(httpTransport, jsonFactory)
        .setAudience(java.util.Collections.singletonList(googleClientId))
        .build();

      com.google.api.client.googleapis.auth.oauth2.GoogleIdToken idToken = verifier.verify(req.getIdToken());
      if (idToken == null) {
        throw new AuthenticationException("Invalid Google ID token");
      }

      var payload = idToken.getPayload();
      String email = (String) payload.get("email");
      Boolean emailVerified = (Boolean) payload.get("email_verified");
      String sub = payload.getSubject();

      if (email == null || Boolean.FALSE.equals(emailVerified)) {
        throw new AuthenticationException("Google account email is not available or not verified");
      }

      // Lookup or provision user
      User user = userRepository.findByEmail(email).orElseGet(() -> {
        // Federated users have no local password — store null so hasPassword() reports false
        User u = new User(email, email, null, (String) payload.get("given_name"), (String) payload.get("family_name"));
        u.setActive(true);
        u.setProvider(AuthProvider.GOOGLE);
        u.setProviderId(sub);
        Role memberRole = roleRepository.findByName(ERole.ROLE_MEMBER.name())
          .orElseThrow(() -> new ResourceNotFoundException("Role", "name", ERole.ROLE_MEMBER));
        java.util.Set<Role> roles = new java.util.HashSet<>();
        roles.add(memberRole);
        u.setRoles(roles);
        return userRepository.save(u);
      });

      String accessToken = jwtUtils.generateTokenFromUsername(email);
      String refresh = jwtUtils.generateRefreshTokenFromUsername(email);
      java.util.List<String> roles = user.getRoles().stream().map(r -> r.getName()).collect(java.util.stream.Collectors.toList());

      return ResponseEntity.ok(new JwtResponse(accessToken, refresh, user.getId() != null ? user.getId().toString() : null, user.getUsername(), user.getEmail(), roles));
    } catch (AuthenticationException e) {
      throw e;
    } catch (Exception e) {
      throw new AuthenticationException("Google ID token verification failed", e);
    }
  }
}
