package com.roots.mms.security.services;

import com.roots.mms.entity.AuthProvider;
import com.roots.mms.entity.ERole;
import com.roots.mms.entity.Role;
import com.roots.mms.entity.User;
import com.roots.mms.repository.RoleRepository;
import com.roots.mms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;

  @Value("${app.auth.auto-provision:false}")
  private boolean autoProvision;

  private static boolean isLikelyEmail(String s) {
    return s != null && s.contains("@") && s.indexOf('@') > 0 && s.indexOf('@') < s.length() - 1;
  }

  private static String extractFirst(String email) {
    String local = email != null ? email.split("@")[0] : "user";
    // Try to split on common separators to get a reasonable first name
    String[] parts = local.split("[._-]");
    String first = parts.length > 0 ? parts[0] : local;
    return first.isBlank() ? "User" : capitalize(first);
  }

  private static String extractLast(String email) {
    String local = email != null ? email.split("@")[0] : "user";
    String[] parts = local.split("[._-]");
    String last = parts.length > 1 ? parts[parts.length - 1] : "User";
    return last.isBlank() ? "User" : capitalize(last);
  }

  private static String capitalize(String s) {
    if (s == null || s.isEmpty()) return s;
    return Character.toUpperCase(s.charAt(0)) + (s.length() > 1 ? s.substring(1) : "");
  }

  @Override
  @Transactional
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    Optional<User> found = userRepository.findByUsernameOrEmail(username, username);
    if (found.isPresent()) {
      return UserPrincipal.create(found.get());
    }

    if (autoProvision && isLikelyEmail(username)) {
      log.info("Auto-provisioning user for subject/email: {}", username);
      User user = new User(username, username, randomEncodedPassword(), extractFirst(username), extractLast(username));
      user.setActive(true);
      user.setProvider(AuthProvider.LOCAL);
      user.setProviderId(null);
      Set<Role> roles = new HashSet<>();
      Role userRole = roleRepository.findByName(ERole.ROLE_USER)
        .orElseGet(() -> roleRepository.save(new Role(ERole.ROLE_USER)));
      roles.add(userRole);
      user.setRoles(roles);
      User saved = userRepository.save(user);
      return UserPrincipal.create(saved);
    }

    throw new UsernameNotFoundException("User Not Found with username or email: " + username);
  }

  private String randomEncodedPassword() {
    byte[] bytes = new byte[16];
    new SecureRandom().nextBytes(bytes);
    return passwordEncoder.encode(java.util.Base64.getEncoder().encodeToString(bytes));
  }
}
