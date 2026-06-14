package com.roots.mms;

import tools.jackson.databind.ObjectMapper;
import com.roots.mms.entity.ERole;
import com.roots.mms.entity.Role;
import com.roots.mms.entity.User;
import com.roots.mms.repository.MemberRepository;
import com.roots.mms.repository.RoleRepository;
import com.roots.mms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * Shared test base: boots a singleton PostgreSQL 16 Testcontainer and lets
 * Spring Boot's {@link ServiceConnection} wire the datasource / Flyway / JPA
 * properties automatically — no manual property plumbing needed.
 *
 * <p>Integration tests extend this class to pick up the container plus the
 * MockMvc + repositories + password encoder wiring. Per-test cleanup deletes
 * members first (FK to users) then users.
 */
@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.MOCK,
  properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration,org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration"
  }
)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

  // Singleton Testcontainers PostgreSQL — shared across all test classes and contexts.
  // Not using @Container/@Testcontainers: those stop the container between contexts,
  // which slows down the suite and churns Flyway migrations unnecessarily.
  @ServiceConnection
  static final PostgreSQLContainer<?> postgres;
  static {
    postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("roots_mms_test")
            .withUsername("test")
            .withPassword("test");
    postgres.start();
  }

  @Autowired
  private WebApplicationContext webApplicationContext;

  protected MockMvc mockMvc;
  @Autowired
  protected ObjectMapper objectMapper;
  @Autowired
  protected UserRepository userRepository;
  @Autowired
  protected RoleRepository roleRepository;
  @Autowired
  protected PasswordEncoder passwordEncoder;
  @Autowired
  protected MemberRepository memberRepository;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .build();
    // clean members first to avoid FK violations, then users
    memberRepository.deleteAll();
    userRepository.deleteAll();
  }

  protected User createUser(String username, String email, String rawPassword, List<ERole> roles) {
    User u = new User(username, email, passwordEncoder.encode(rawPassword), "Test", "User");
    Set<Role> rs = new HashSet<>();
    for (ERole r : roles) {
      rs.add(roleRepository.findByName(r.name()).orElseThrow());
    }
    u.setRoles(rs);
    u.setActive(true);
    return userRepository.save(u);
  }
}
