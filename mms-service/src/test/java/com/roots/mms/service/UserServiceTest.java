package com.roots.mms.service;

import com.roots.mms.dto.request.ChangePasswordRequest;
import com.roots.mms.dto.request.UpdateUserProfileRequest;
import com.roots.mms.dto.request.UpdateUserRolesRequest;
import com.roots.mms.dto.request.UpdateUserStatusRequest;
import com.roots.mms.dto.response.UserResponse;
import com.roots.mms.entity.AuthProvider;
import com.roots.mms.entity.Role;
import com.roots.mms.entity.User;
import com.roots.mms.exception.BusinessRuleException;
import com.roots.mms.exception.DuplicateResourceException;
import com.roots.mms.exception.ResourceNotFoundException;
import com.roots.mms.repository.RoleRepository;
import com.roots.mms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private UserRepository userRepo;
    private RoleRepository roleRepo;
    private PasswordEncoder passwordEncoder;
    private VerificationTokenService verificationTokenService;
    private UserService service;

    @BeforeEach
    void setUp() {
        userRepo = mock(UserRepository.class);
        roleRepo = mock(RoleRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        verificationTokenService = mock(VerificationTokenService.class);
        service = new UserService(userRepo, roleRepo, passwordEncoder, verificationTokenService);
    }

    private User localUser() {
        User u = new User("alice", "alice@example.com", "encoded-pw", "Alice", "Smith");
        u.setId(UUID.randomUUID());
        u.setProvider(AuthProvider.LOCAL);
        u.setActive(true);
        return u;
    }

    private User googleUser() {
        User u = localUser();
        u.setProvider(AuthProvider.GOOGLE);
        u.setProviderId("google-sub-123");
        return u;
    }

    // ── getUserById ───────────────────────────────────────────────────

    @Test
    void getUserById_unknown_throws() {
        UUID id = UUID.randomUUID();
        when(userRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUserById(id.toString()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getUserById_returnsResponse() {
        User u = localUser();
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));

        UserResponse resp = service.getUserById(u.getId().toString());

        assertThat(resp.getEmail()).isEqualTo("alice@example.com");
        assertThat(resp.getProvider()).isEqualTo("LOCAL");
        assertThat(resp.getHasPassword()).isTrue();
    }

    // ── updateProfile ─────────────────────────────────────────────────

    @Test
    void updateProfile_emailChange_blockedViaProfilePut() {
        // Silent email swap through the profile PUT is closed — all real email
        // changes must go through the verified change-email flow.
        User u = localUser();
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));
        UpdateUserProfileRequest req = new UpdateUserProfileRequest();
        req.setEmail("new@example.com");

        assertThatThrownBy(() -> service.updateProfile(u.getId().toString(), req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("verified change-email");
        verify(userRepo, never()).save(any());
    }

    @Test
    void updateProfile_emailChange_caseChangeOnlyIsNoOp() {
        // Same address, just different case — must NOT call existsByEmail
        // (which would otherwise self-collide) or block on federated users.
        User u = googleUser();
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));
        UpdateUserProfileRequest req = new UpdateUserProfileRequest();
        req.setEmail("ALICE@example.com");

        UserResponse resp = service.updateProfile(u.getId().toString(), req);

        assertThat(resp.getEmail()).isEqualTo("alice@example.com");
        verify(userRepo, never()).existsByEmail(anyString());
    }

    @Test
    void updateProfile_emailChange_neverPersistsViaProfilePut() {
        // Even for a brand-new, available address, the profile PUT refuses the
        // change and persists nothing — the verified flow is the only path.
        User u = localUser();
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));
        UpdateUserProfileRequest req = new UpdateUserProfileRequest();
        req.setEmail("new@example.com");

        assertThatThrownBy(() -> service.updateProfile(u.getId().toString(), req))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(u.getEmail()).isEqualTo("alice@example.com");
        verify(userRepo, never()).save(any());
    }

    @Test
    void updateProfile_username_changesWhenValidAndUnique() {
        User u = localUser(); // username "alice"
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));
        when(userRepo.existsByUsername("alice2")).thenReturn(false);
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        UpdateUserProfileRequest req = new UpdateUserProfileRequest();
        req.setUsername("alice2");

        service.updateProfile(u.getId().toString(), req);

        assertThat(u.getUsername()).isEqualTo("alice2");
        assertThat(u.getUsernameChangedAt()).isNotNull();
    }

    @Test
    void updateProfile_username_duplicate_throws() {
        User u = localUser();
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));
        when(userRepo.existsByUsername("taken")).thenReturn(true);
        UpdateUserProfileRequest req = new UpdateUserProfileRequest();
        req.setUsername("taken");

        assertThatThrownBy(() -> service.updateProfile(u.getId().toString(), req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("taken");
        assertThat(u.getUsername()).isEqualTo("alice");
    }

    @Test
    void updateProfile_username_withinCooldown_throws() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "usernameCooldownDays", 30);
        User u = localUser();
        u.setUsernameChangedAt(java.time.LocalDateTime.now().minusDays(5));
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));
        UpdateUserProfileRequest req = new UpdateUserProfileRequest();
        req.setUsername("alice3");

        assertThatThrownBy(() -> service.updateProfile(u.getId().toString(), req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("changed recently");
        assertThat(u.getUsername()).isEqualTo("alice");
        verify(userRepo, never()).existsByUsername(anyString());
    }

    @Test
    void updateProfile_blankFirstOrLastName_throws() {
        User u = localUser();
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));

        UpdateUserProfileRequest blankFirst = new UpdateUserProfileRequest();
        blankFirst.setFirstName("   ");
        assertThatThrownBy(() -> service.updateProfile(u.getId().toString(), blankFirst))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("First name");

        UpdateUserProfileRequest blankLast = new UpdateUserProfileRequest();
        blankLast.setLastName("   ");
        assertThatThrownBy(() -> service.updateProfile(u.getId().toString(), blankLast))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Last name");
    }

    @Test
    void updateProfile_invalidPhone_throws() {
        User u = localUser();
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));
        UpdateUserProfileRequest req = new UpdateUserProfileRequest();
        req.setPhoneNumber("abc!?");

        assertThatThrownBy(() -> service.updateProfile(u.getId().toString(), req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("phone");
    }

    @Test
    void updateProfile_emptyPhone_clearsField() {
        User u = localUser();
        u.setPhoneNumber("+1-555-0000");
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));
        UpdateUserProfileRequest req = new UpdateUserProfileRequest();
        req.setPhoneNumber("   ");

        service.updateProfile(u.getId().toString(), req);

        assertThat(u.getPhoneNumber()).isNull();
    }

    @Test
    void updateProfile_validPhone_setsTrimmed() {
        User u = localUser();
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));
        UpdateUserProfileRequest req = new UpdateUserProfileRequest();
        req.setPhoneNumber("  +1 (555) 123-4567  ");

        service.updateProfile(u.getId().toString(), req);

        assertThat(u.getPhoneNumber()).isEqualTo("+1 (555) 123-4567");
    }

    // ── adminSetPassword ──────────────────────────────────────────────

    @Test
    void adminSetPassword_unknownUser_throws() {
        UUID id = UUID.randomUUID();
        when(userRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.adminSetPassword(id.toString(), "Abcdefg1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void adminSetPassword_federatedUser_throws() {
        User u = googleUser();
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.adminSetPassword(u.getId().toString(), "Abcdefg1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Google");
    }

    @Test
    void adminSetPassword_weakPassword_throws() {
        User u = localUser();
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.adminSetPassword(u.getId().toString(), "short"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.adminSetPassword(u.getId().toString(), "abcdefgh")) // no digit
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.adminSetPassword(u.getId().toString(), "12345678")) // no letter
                .isInstanceOf(BusinessRuleException.class);
        verify(userRepo, never()).save(any());
    }

    @Test
    void adminSetPassword_valid_encodesAndSaves() {
        User u = localUser();
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));
        when(passwordEncoder.encode("Abcdefg1")).thenReturn("hashed");

        service.adminSetPassword(u.getId().toString(), "Abcdefg1");

        assertThat(u.getPassword()).isEqualTo("hashed");
        verify(userRepo).save(u);
    }

    // ── changePassword ────────────────────────────────────────────────

    @Test
    void changePassword_federatedUser_throws() {
        User u = googleUser();
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("any");
        req.setNewPassword("Abcdefg1");

        assertThatThrownBy(() -> service.changePassword(u.getId().toString(), req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Google");
    }

    @Test
    void changePassword_blankStoredPassword_throws() {
        User u = localUser();
        u.setPassword("   ");
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("any");
        req.setNewPassword("Abcdefg1");

        assertThatThrownBy(() -> service.changePassword(u.getId().toString(), req))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void changePassword_wrongCurrent_throws() {
        User u = localUser();
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("wrong", "encoded-pw")).thenReturn(false);
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("wrong");
        req.setNewPassword("Abcdefg1");

        assertThatThrownBy(() -> service.changePassword(u.getId().toString(), req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Current password is incorrect");
    }

    @Test
    void changePassword_weakNew_throws() {
        User u = localUser();
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("oldpw", "encoded-pw")).thenReturn(true);
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("oldpw");
        req.setNewPassword("weak");

        assertThatThrownBy(() -> service.changePassword(u.getId().toString(), req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("New password");
    }

    @Test
    void changePassword_sameAsCurrent_throws() {
        User u = localUser();
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("Abcdefg1", "encoded-pw")).thenReturn(true);
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("Abcdefg1");
        req.setNewPassword("Abcdefg1");

        assertThatThrownBy(() -> service.changePassword(u.getId().toString(), req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("different");
    }

    @Test
    void changePassword_valid_encodesAndSaves() {
        User u = localUser();
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("oldpw", "encoded-pw")).thenReturn(true);
        when(passwordEncoder.matches("NewPass1", "encoded-pw")).thenReturn(false);
        when(passwordEncoder.encode("NewPass1")).thenReturn("new-hash");
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("oldpw");
        req.setNewPassword("NewPass1");

        service.changePassword(u.getId().toString(), req);

        assertThat(u.getPassword()).isEqualTo("new-hash");
        verify(userRepo).save(u);
    }

    // ── listUsers ─────────────────────────────────────────────────────

    @Test
    void listUsers_buildsPageRequestWithSort() {
        when(userRepo.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(localUser())));

        Page<UserResponse> page = service.listUsers(0, 10, "createdAt", "desc");

        assertThat(page.getContent()).hasSize(1);
        var captor = forClass(Pageable.class);
        verify(userRepo).findAll(captor.capture());
        Pageable p = captor.getValue();
        assertThat(p.getPageNumber()).isZero();
        assertThat(p.getPageSize()).isEqualTo(10);
        Sort.Order order = p.getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.isDescending()).isTrue();
    }

    @Test
    void listUsers_ascSortDirection() {
        when(userRepo.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listUsers(2, 5, "username", "asc");

        var captor = forClass(Pageable.class);
        verify(userRepo).findAll(captor.capture());
        Sort.Order order = captor.getValue().getSort().getOrderFor("username");
        assertThat(order).isNotNull();
        assertThat(order.isAscending()).isTrue();
    }

    // ── updateRoles ───────────────────────────────────────────────────

    @Test
    void updateRoles_unknownRoleName_throws() {
        User u = localUser();
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));
        when(roleRepo.findByName("ROLE_GHOST")).thenReturn(Optional.empty());
        UpdateUserRolesRequest req = new UpdateUserRolesRequest();
        req.setRoles(List.of("ROLE_GHOST"));

        assertThatThrownBy(() -> service.updateRoles(u.getId().toString(), req))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(userRepo, never()).save(any());
    }

    @Test
    void updateRoles_replacesUserRoleSet() {
        User u = localUser();
        u.getRoles().add(new Role("ROLE_OLD"));
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));
        Role admin = new Role("ROLE_ADMIN");
        Role member = new Role("ROLE_MEMBER");
        when(roleRepo.findByName("ROLE_ADMIN")).thenReturn(Optional.of(admin));
        when(roleRepo.findByName("ROLE_MEMBER")).thenReturn(Optional.of(member));
        UpdateUserRolesRequest req = new UpdateUserRolesRequest();
        req.setRoles(List.of("ROLE_ADMIN", "ROLE_MEMBER"));

        service.updateRoles(u.getId().toString(), req);

        assertThat(u.getRoles()).containsExactlyInAnyOrder(admin, member);
        verify(userRepo).save(u);
    }

    // ── updateStatus ──────────────────────────────────────────────────

    @Test
    void updateStatus_setsActive() {
        User u = localUser();
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));
        UpdateUserStatusRequest req = new UpdateUserStatusRequest();
        req.setActive(false);

        service.updateStatus(u.getId().toString(), req);

        assertThat(u.getActive()).isFalse();
        verify(userRepo).save(u);
    }

    // ── deleteUser ────────────────────────────────────────────────────

    @Test
    void deleteUser_unknown_throws() {
        UUID id = UUID.randomUUID();
        when(userRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteUser(id.toString()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteUser_existing_deletes() {
        User u = localUser();
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));

        service.deleteUser(u.getId().toString());

        verify(userRepo).delete(u);
    }

    // ── updateProvider ────────────────────────────────────────────────

    @Test
    void updateProvider_unknownEnumValue_throws() {
        User u = localUser();
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.updateProvider(u.getId().toString(), "FACEBOOK"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not supported");
    }

    @Test
    void updateProvider_setLocal_clearsProviderId() {
        User u = googleUser();
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));

        service.updateProvider(u.getId().toString(), "LOCAL");

        assertThat(u.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(u.getProviderId()).isNull();
    }

    @Test
    void updateProvider_setGoogle_keepsProviderId() {
        User u = localUser();
        u.setProviderId(null);
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));

        service.updateProvider(u.getId().toString(), "GOOGLE");

        assertThat(u.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        // Switching TO a federated provider must not invent a providerId — that's
        // the OAuth subject and only the OAuth flow can set it.
        assertThat(u.getProviderId()).isNull();
    }

    // ── disconnectProvider ────────────────────────────────────────────

    @Test
    void disconnectProvider_alreadyLocal_throws() {
        User u = localUser();
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.disconnectProvider(u.getId().toString(), "Abcdefg1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not connected");
    }

    @Test
    void disconnectProvider_noLocalPassword_requiresValidNewPassword() {
        User u = googleUser();
        u.setPassword(null);
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));

        // Weak new password rejected — lockout protection.
        assertThatThrownBy(() -> service.disconnectProvider(u.getId().toString(), "weak"))
                .isInstanceOf(BusinessRuleException.class);

        // Null also rejected.
        assertThatThrownBy(() -> service.disconnectProvider(u.getId().toString(), null))
                .isInstanceOf(BusinessRuleException.class);
        verify(userRepo, never()).save(any());
    }

    @Test
    void disconnectProvider_noLocalPassword_validNew_setsPasswordAndFlipsToLocal() {
        User u = googleUser();
        u.setPassword(null);
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));
        when(passwordEncoder.encode("Abcdefg1")).thenReturn("hashed");

        UserResponse resp = service.disconnectProvider(u.getId().toString(), "Abcdefg1");

        assertThat(u.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(u.getProviderId()).isNull();
        assertThat(u.getPassword()).isEqualTo("hashed");
        assertThat(resp.getProvider()).isEqualTo("LOCAL");
        verify(userRepo).save(u);
    }

    @Test
    void disconnectProvider_existingLocalPassword_keepsItAndIgnoresNewPassword() {
        // User had previously set up a local password before linking to Google.
        // Disconnecting should NOT require a new password, and should NOT
        // overwrite the existing one.
        User u = googleUser();
        u.setPassword("existing-hash");
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));

        service.disconnectProvider(u.getId().toString(), null);

        assertThat(u.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(u.getPassword()).isEqualTo("existing-hash");
        verify(passwordEncoder, never()).encode(any());
    }

    // ── toResponse mapping ────────────────────────────────────────────

    @Test
    void toResponse_mapsFlagsAndRoles() {
        User u = localUser();
        u.setEmailVerified(true);
        u.setTotpEnabled(true);
        Role r = new Role("ROLE_ADMIN");
        Set<Role> roles = new HashSet<>();
        roles.add(r);
        u.setRoles(roles);

        UserResponse resp = service.toResponse(u);

        assertThat(resp.getEmailVerified()).isTrue();
        assertThat(resp.getTwoFactorEnabled()).isTrue();
        assertThat(resp.getRoles()).containsExactly("ROLE_ADMIN");
    }

    @Test
    void toResponse_blankPassword_mapsHasPasswordFalse() {
        User u = localUser();
        u.setPassword("   ");

        UserResponse resp = service.toResponse(u);

        assertThat(resp.getHasPassword()).isFalse();
    }

    @Test
    void toResponse_nullProvider_defaultsToLocal() {
        User u = localUser();
        u.setProvider(null);

        UserResponse resp = service.toResponse(u);

        assertThat(resp.getProvider()).isEqualTo("LOCAL");
    }
}
