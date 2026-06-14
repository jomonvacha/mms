package com.roots.mms.security.services;

import com.roots.mms.entity.AuthProvider;
import com.roots.mms.entity.ERole;
import com.roots.mms.entity.Role;
import com.roots.mms.entity.User;
import com.roots.mms.repository.RoleRepository;
import com.roots.mms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserDetailsServiceImplTest {

    private UserRepository userRepo;
    private RoleRepository roleRepo;
    private PasswordEncoder passwordEncoder;
    private UserDetailsServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepo = mock(UserRepository.class);
        roleRepo = mock(RoleRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new UserDetailsServiceImpl(userRepo, roleRepo, passwordEncoder);
        ReflectionTestUtils.setField(service, "autoProvision", false);
    }

    private User activeUser(String username, String email) {
        User user = new User(username, email, "encoded-pw", "First", "Last");
        user.setId(UUID.randomUUID());
        user.setActive(true);
        user.setProvider(AuthProvider.LOCAL);
        user.setRoles(Set.of(new Role(ERole.ROLE_MEMBER)));
        return user;
    }

    @Test
    void loadUserByUsername_existingUser_returnsUserPrincipal() {
        User user = activeUser("alice", "alice@test.com");
        when(userRepo.findByUsernameOrEmail("alice", "alice")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("alice");

        assertThat(details.getUsername()).isEqualTo("alice");
        assertThat(details).isInstanceOf(UserPrincipal.class);
    }

    @Test
    void loadUserByUsername_notFound_noAutoProvision_throwsUsernameNotFoundException() {
        when(userRepo.findByUsernameOrEmail(anyString(), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void loadUserByUsername_emailLookup_matchesByEmail() {
        User user = activeUser("bob", "bob@test.com");
        when(userRepo.findByUsernameOrEmail("bob@test.com", "bob@test.com")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("bob@test.com");

        assertThat(details.getUsername()).isEqualTo("bob");
    }

    @Test
    void loadUserByUsername_inactiveUser_isEnabledFalse() {
        User user = activeUser("inactive", "inactive@test.com");
        user.setActive(false);
        when(userRepo.findByUsernameOrEmail("inactive", "inactive")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("inactive");

        assertThat(details.isEnabled()).isFalse();
    }

    @Test
    void loadUserByUsername_activeUser_isEnabledTrue() {
        User user = activeUser("active", "active@test.com");
        when(userRepo.findByUsernameOrEmail("active", "active")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("active");

        assertThat(details.isEnabled()).isTrue();
    }

    @Test
    void loadUserByUsername_rolesAreMappedToAuthorities() {
        User user = activeUser("carol", "carol@test.com");
        user.setRoles(Set.of(new Role(ERole.ROLE_ADMIN)));
        when(userRepo.findByUsernameOrEmail("carol", "carol")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("carol");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_ADMIN");
    }

    @Test
    void loadUserByUsername_autoProvisionEnabled_nonEmailSubject_throws() {
        ReflectionTestUtils.setField(service, "autoProvision", true);
        when(userRepo.findByUsernameOrEmail("plainusername", "plainusername"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("plainusername"))
                .isInstanceOf(UsernameNotFoundException.class);
        verify(userRepo, never()).save(any());
    }

    @Test
    void loadUserByUsername_autoProvisionEnabled_emailSubject_createsUser() {
        ReflectionTestUtils.setField(service, "autoProvision", true);
        when(userRepo.findByUsernameOrEmail("new@example.com", "new@example.com"))
                .thenReturn(Optional.empty());
        Role memberRole = new Role(ERole.ROLE_MEMBER);
        when(roleRepo.findByName(ERole.ROLE_MEMBER.name())).thenReturn(Optional.of(memberRole));
        when(passwordEncoder.encode(anyString())).thenReturn("random-encoded");

        User saved = activeUser("new@example.com", "new@example.com");
        saved.setRoles(Set.of(memberRole));
        when(userRepo.save(any(User.class))).thenReturn(saved);

        UserDetails details = service.loadUserByUsername("new@example.com");

        assertThat(details).isNotNull();
        verify(userRepo).save(any(User.class));
    }
}
