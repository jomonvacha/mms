package com.roots.mms.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBlacklistServiceTest {

    private TokenBlacklistService service;

    @BeforeEach
    void setUp() {
        service = new TokenBlacklistService();
    }

    @Test
    void isRevoked_unknownToken_returnsFalse() {
        assertThat(service.isRevoked("not-in-list")).isFalse();
    }

    @Test
    void isRevoked_nullToken_returnsFalse() {
        assertThat(service.isRevoked(null)).isFalse();
    }

    @Test
    void revoke_thenIsRevoked_returnsTrue() {
        long futureExpiry = System.currentTimeMillis() + 60_000;
        service.revoke("tok-abc", futureExpiry);

        assertThat(service.isRevoked("tok-abc")).isTrue();
    }

    @Test
    void revoke_alreadyExpiredToken_isNotRevoked() {
        long pastExpiry = System.currentTimeMillis() - 1;
        service.revoke("old-tok", pastExpiry);

        assertThat(service.isRevoked("old-tok")).isFalse();
    }

    @Test
    void revoke_alreadyExpiredToken_removedFromBlacklist() {
        long pastExpiry = System.currentTimeMillis() - 1;
        service.revoke("old-tok", pastExpiry);
        service.isRevoked("old-tok");

        assertThat(service.size()).isZero();
    }

    @Test
    void revoke_nullToken_isIgnored() {
        service.revoke(null, System.currentTimeMillis() + 60_000);

        assertThat(service.size()).isZero();
    }

    @Test
    void revoke_blankToken_isIgnored() {
        service.revoke("   ", System.currentTimeMillis() + 60_000);

        assertThat(service.size()).isZero();
    }

    @Test
    void size_reflectsRevokedCount() {
        service.revoke("t1", System.currentTimeMillis() + 60_000);
        service.revoke("t2", System.currentTimeMillis() + 60_000);

        assertThat(service.size()).isEqualTo(2);
    }

    @Test
    void revoke_sameTokenTwice_doesNotDuplicateEntry() {
        long expiry = System.currentTimeMillis() + 60_000;
        service.revoke("dup-tok", expiry);
        service.revoke("dup-tok", expiry);

        assertThat(service.size()).isEqualTo(1);
    }
}
