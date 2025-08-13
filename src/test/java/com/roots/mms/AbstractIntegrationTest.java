package com.roots.mms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roots.mms.entity.ERole;
import com.roots.mms.entity.Role;
import com.roots.mms.entity.User;
import com.roots.mms.repository.MemberRepository;
import com.roots.mms.repository.RoleRepository;
import com.roots.mms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Autowired
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
    void clearUsers() {
        // clean members first to avoid FK violations, then users
        memberRepository.deleteAll();
        userRepository.deleteAll();
    }

    protected User createUser(String username, String email, String rawPassword, List<ERole> roles) {
        User u = new User(username, email, passwordEncoder.encode(rawPassword), "Test", "User");
        Set<Role> rs = new HashSet<>();
        for (ERole r : roles) {
            rs.add(roleRepository.findByName(r).orElseThrow());
        }
        u.setRoles(rs);
        u.setActive(true);
        return userRepository.save(u);
    }
}
