package com.roots.mms.security.services;

import com.roots.mms.entity.ERole;
import com.roots.mms.entity.Role;
import com.roots.mms.entity.User;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserPrincipalTest {

    private User buildUser(boolean active, String... roleNames) {
        User user = new User("user1", "user1@test.com", "pw", "First", "Last");
        user.setId(UUID.randomUUID());
        user.setActive(active);
        Set<Role> roles = new java.util.HashSet<>();
        for (String name : roleNames) {
            roles.add(new Role(name));
        }
        user.setRoles(roles);
        return user;
    }

    @Test
    void create_mapsUsername() {
        User user = buildUser(true, ERole.ROLE_MEMBER.name());
        UserPrincipal p = UserPrincipal.create(user);

        assertThat(p.getUsername()).isEqualTo("user1");
    }

    @Test
    void create_mapsEmail() {
        User user = buildUser(true, ERole.ROLE_MEMBER.name());
        UserPrincipal p = UserPrincipal.create(user);

        assertThat(p.getEmail()).isEqualTo("user1@test.com");
    }

    @Test
    void create_mapsId_asString() {
        User user = buildUser(true, ERole.ROLE_MEMBER.name());
        UserPrincipal p = UserPrincipal.create(user);

        assertThat(p.getId()).isEqualTo(user.getId().toString());
    }

    @Test
    void create_nullId_mapsToNull() {
        User user = buildUser(true, ERole.ROLE_MEMBER.name());
        user.setId(null);
        UserPrincipal p = UserPrincipal.create(user);

        assertThat(p.getId()).isNull();
    }

    @Test
    void create_activeTrue_isEnabledTrue() {
        UserPrincipal p = UserPrincipal.create(buildUser(true));

        assertThat(p.isEnabled()).isTrue();
    }

    @Test
    void create_activeFalse_isEnabledFalse() {
        UserPrincipal p = UserPrincipal.create(buildUser(false));

        assertThat(p.isEnabled()).isFalse();
    }

    @Test
    void create_multipleRoles_allMappedAsAuthorities() {
        User user = buildUser(true, "ROLE_ADMIN", "ROLE_MEMBER");
        UserPrincipal p = UserPrincipal.create(user);

        assertThat(p.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_MEMBER");
    }

    @Test
    void create_noRoles_emptyAuthorities() {
        User user = buildUser(true);
        UserPrincipal p = UserPrincipal.create(user);

        assertThat(p.getAuthorities()).isEmpty();
    }

    @Test
    void springSecurityFlags_alwaysTrue() {
        UserPrincipal p = UserPrincipal.create(buildUser(true));

        assertThat(p.isAccountNonExpired()).isTrue();
        assertThat(p.isAccountNonLocked()).isTrue();
        assertThat(p.isCredentialsNonExpired()).isTrue();
    }

    @Test
    void equals_sameId_returnsTrue() {
        User user = buildUser(true);
        UserPrincipal a = UserPrincipal.create(user);
        UserPrincipal b = new UserPrincipal(
                user.getId().toString(), "other", "other@test.com", "pw",
                a.getAuthorities(), false);

        assertThat(a).isEqualTo(b);
    }

    @Test
    void equals_differentId_returnsFalse() {
        User u1 = buildUser(true);
        User u2 = buildUser(true);
        UserPrincipal a = UserPrincipal.create(u1);
        UserPrincipal b = UserPrincipal.create(u2);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void hashCode_sameId_sameHash() {
        User user = buildUser(true);
        UserPrincipal a = UserPrincipal.create(user);
        UserPrincipal b = new UserPrincipal(
                user.getId().toString(), "other", "other@test.com", "pw",
                a.getAuthorities(), false);

        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
