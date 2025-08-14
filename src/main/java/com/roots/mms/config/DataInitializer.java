package com.roots.mms.config;

import com.roots.mms.entity.ERole;
import com.roots.mms.entity.Role;
import com.roots.mms.entity.User;
import com.roots.mms.repository.RoleRepository;
import com.roots.mms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.enabled:false}")
    private boolean adminEnabled;
    @Value("${app.admin.username:admin}")
    private String adminUsername;
    @Value("${app.admin.email:admin@example.com}")
    private String adminEmail;
    @Value("${app.admin.password:admin123}")
    private String adminPassword;
    @Value("${app.admin.first-name:System}")
    private String adminFirstName;
    @Value("${app.admin.last-name:Administrator}")
    private String adminLastName;

    @Override
    public void run(String... args) throws Exception {
        initializeRoles();
        seedAdminUserIfConfigured();
    }

    private void initializeRoles() {
        for (ERole eRole : ERole.values()) {
            if (roleRepository.findByName(eRole).isEmpty()) {
                Role role = new Role(eRole);
                roleRepository.save(role);
                log.info("Created role: {}", eRole.name());
            }
        }
    }

    private void seedAdminUserIfConfigured() {
        if (!adminEnabled) return;

        Optional<User> existingByUsername = userRepository.findByUsername(adminUsername);
        Optional<User> existingByEmail = userRepository.findByEmail(adminEmail);
        if (existingByUsername.isPresent() || existingByEmail.isPresent()) {
            log.info("Admin user already present. Skipping seed.");
            return;
        }

        User admin = new User(adminUsername, adminEmail,
                passwordEncoder.encode(adminPassword), adminFirstName, adminLastName);
        admin.setActive(true);

        Set<Role> roles = new HashSet<>();
        Role adminRole = roleRepository.findByName(ERole.ROLE_ADMIN)
                .orElseThrow(() -> new IllegalStateException("ROLE_ADMIN not initialized"));
        Role userRole = roleRepository.findByName(ERole.ROLE_USER)
                .orElseThrow(() -> new IllegalStateException("ROLE_USER not initialized"));
        roles.add(adminRole);
        roles.add(userRole);
        admin.setRoles(roles);

        userRepository.save(admin);
        log.info("Seeded default admin user: {}", adminUsername);
    }
}
