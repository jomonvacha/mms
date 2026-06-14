package com.roots.mms.migration;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for the FK-translation map the migration tool depends on. Pure
 * in-memory — no Spring, no database, no Testcontainers. The value comes from
 * catching accidental regressions in resolveOrAssign/get/put semantics, which
 * would corrupt FK rewriting during a real migration.
 */
class IdMapTest {

    @Test
    void resolveOrAssignMintsStableUuidPerPair() {
        IdMap map = new IdMap();

        UUID first  = map.resolveOrAssign("users", "507f191e810c19729de860ea");
        UUID second = map.resolveOrAssign("users", "507f191e810c19729de860ea");

        assertThat(second).isEqualTo(first);
    }

    @Test
    void resolveOrAssignNamespacesByCollection() {
        IdMap map = new IdMap();

        UUID usersEntry   = map.resolveOrAssign("users",   "abc");
        UUID membersEntry = map.resolveOrAssign("members", "abc");

        assertThat(usersEntry).isNotEqualTo(membersEntry);
        assertThat(map.size("users")).isEqualTo(1);
        assertThat(map.size("members")).isEqualTo(1);
        assertThat(map.totalSize()).isEqualTo(2);
    }

    @Test
    void getReturnsNullForUnassignedPair() {
        IdMap map = new IdMap();

        assertThat(map.get("users", "never-seen")).isNull();
        assertThat(map.size("unknown-collection")).isZero();
    }

    @Test
    void putOverwritesResolvedValue() {
        IdMap map = new IdMap();
        UUID minted = map.resolveOrAssign("roles", "role-1");
        UUID existing = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

        map.put("roles", "role-1", existing);

        assertThat(map.get("roles", "role-1")).isEqualTo(existing);
        assertThat(map.resolveOrAssign("roles", "role-1")).isEqualTo(existing);
        assertThat(map.resolveOrAssign("roles", "role-1")).isNotEqualTo(minted);
    }
}
