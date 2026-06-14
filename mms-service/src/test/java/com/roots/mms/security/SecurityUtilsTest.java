package com.roots.mms.security;

import com.roots.mms.entity.ERole;
import com.roots.mms.entity.Role;
import com.roots.mms.entity.User;
import com.roots.mms.exception.AuthenticationException;
import com.roots.mms.security.services.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityUtilsTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private UserPrincipal principalFor(String username) {
        User user = new User(username, username + "@test.com", "pw", "First", "Last");
        user.setId(UUID.randomUUID());
        user.setActive(true);
        user.setRoles(Set.of(new Role(ERole.ROLE_MEMBER)));
        return UserPrincipal.create(user);
    }

    private void authenticateAs(UserPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @Test
    void getCurrentUserPrincipal_noContext_returnsEmpty() {
        assertThat(SecurityUtils.getCurrentUserPrincipal()).isEmpty();
    }

    @Test
    void getCurrentUserPrincipal_withUserPrincipal_returnsIt() {
        UserPrincipal principal = principalFor("alice");
        authenticateAs(principal);

        assertThat(SecurityUtils.getCurrentUserPrincipal()).contains(principal);
    }

    @Test
    void getCurrentUserPrincipal_nonUserPrincipalAuthentication_returnsEmpty() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")))
        );

        assertThat(SecurityUtils.getCurrentUserPrincipal()).isEmpty();
    }

    @Test
    void getCurrentUserIdOrNull_authenticated_returnsId() {
        UserPrincipal principal = principalFor("bob");
        authenticateAs(principal);

        assertThat(SecurityUtils.getCurrentUserIdOrNull()).isEqualTo(principal.getId());
    }

    @Test
    void getCurrentUserIdOrNull_unauthenticated_returnsNull() {
        assertThat(SecurityUtils.getCurrentUserIdOrNull()).isNull();
    }

    @Test
    void getCurrentUserIdOrThrow_authenticated_returnsId() {
        UserPrincipal principal = principalFor("carol");
        authenticateAs(principal);

        assertThat(SecurityUtils.getCurrentUserIdOrThrow()).isEqualTo(principal.getId());
    }

    @Test
    void getCurrentUserIdOrThrow_unauthenticated_throwsAuthenticationException() {
        assertThatThrownBy(SecurityUtils::getCurrentUserIdOrThrow)
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void getCurrentUsernameOrNull_authenticated_returnsName() {
        UserPrincipal principal = principalFor("dana");
        authenticateAs(principal);

        assertThat(SecurityUtils.getCurrentUsernameOrNull()).isEqualTo("dana");
    }

    @Test
    void getCurrentUsernameOrNull_noContext_returnsNull() {
        assertThat(SecurityUtils.getCurrentUsernameOrNull()).isNull();
    }

    @Test
    void getCurrentUsernameOrNull_anonymousUser_returnsNull() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")))
        );

        assertThat(SecurityUtils.getCurrentUsernameOrNull()).isNull();
    }
}
