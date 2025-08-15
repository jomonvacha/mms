package com.roots.mms.controller;

import com.roots.mms.dto.request.GoogleIdTokenRequest;
import com.roots.mms.dto.request.LoginRequest;
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
import com.roots.mms.security.services.UserPrincipal;
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

@CrossOrigin(origins = "*", maxAge = 3600)
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

      SecurityContextHolder.getContext().setAuthentication(authentication);
      String jwt = jwtUtils.generateJwtToken(authentication);
      String refreshToken = jwtUtils.generateRefreshToken(authentication);

      UserPrincipal userDetails = (UserPrincipal) authentication.getPrincipal();
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

  @PostMapping("/signup")
  public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
    log.info("User registration attempt for username: {}", signUpRequest.getUsername());

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

    Role userRole = roleRepository.findByName(ERole.ROLE_USER)
      .orElseThrow(() -> new ResourceNotFoundException("Role", "name", ERole.ROLE_USER));
    roles.add(userRole);

    user.setRoles(roles);
    userRepository.save(user);

    log.info("Successfully registered user: {}", signUpRequest.getUsername());

    return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
  }

  @PostMapping("/refresh")
  public ResponseEntity<?> refreshToken(@RequestParam String refreshToken) {
    if (jwtUtils.validateJwtToken(refreshToken)) {
      String username = jwtUtils.getUserNameFromJwtToken(refreshToken);
      User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));

      String newJwt = jwtUtils.generateTokenFromUsername(user.getUsername());
      String newRefreshToken = jwtUtils.generateRefreshTokenFromUsername(user.getUsername());

      List<String> roles = user.getRoles().stream()
        .map(r -> r.getName().name())
        .collect(Collectors.toList());

      return ResponseEntity.ok(new JwtResponse(newJwt, newRefreshToken,
        user.getId(),
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
        User u = new User(email, email, "", (String) payload.get("given_name"), (String) payload.get("family_name"));
        u.setActive(true);
        u.setProvider(AuthProvider.GOOGLE);
        u.setProviderId(sub);
        Role userRole = roleRepository.findByName(ERole.ROLE_USER)
          .orElseThrow(() -> new ResourceNotFoundException("Role", "name", ERole.ROLE_USER));
        java.util.Set<Role> roles = new java.util.HashSet<>();
        roles.add(userRole);
        u.setRoles(roles);
        return userRepository.save(u);
      });

      String accessToken = jwtUtils.generateTokenFromUsername(email);
      String refresh = jwtUtils.generateRefreshTokenFromUsername(email);
      java.util.List<String> roles = user.getRoles().stream().map(r -> r.getName().name()).collect(java.util.stream.Collectors.toList());

      return ResponseEntity.ok(new JwtResponse(accessToken, refresh, user.getId(), user.getUsername(), user.getEmail(), roles));
    } catch (AuthenticationException e) {
      throw e;
    } catch (Exception e) {
      throw new AuthenticationException("Google ID token verification failed", e);
    }
  }
}
