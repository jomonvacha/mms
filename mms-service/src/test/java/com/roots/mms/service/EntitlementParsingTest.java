package com.roots.mms.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit test (no Spring, no Mongo) for the string → primitive helpers
 * that {@link EntitlementService} exposes to callers. These functions sit
 * on every entitlement check, so a parser regression would silently
 * mis-gate features.
 */
class EntitlementParsingTest {

    @Test
    void asBoolean_acceptsTruthyValues() {
        assertThat(EntitlementService.asBoolean("true")).isTrue();
        assertThat(EntitlementService.asBoolean("TRUE")).isTrue();
        assertThat(EntitlementService.asBoolean("  true  ")).isTrue();
        assertThat(EntitlementService.asBoolean("1")).isTrue();
    }

    @Test
    void asBoolean_rejectsFalsyAndUnknownValues() {
        assertThat(EntitlementService.asBoolean(null)).isFalse();
        assertThat(EntitlementService.asBoolean("")).isFalse();
        assertThat(EntitlementService.asBoolean("false")).isFalse();
        assertThat(EntitlementService.asBoolean("FALSE")).isFalse();
        assertThat(EntitlementService.asBoolean("0")).isFalse();
        assertThat(EntitlementService.asBoolean("yes")).isFalse(); // strict: only true/1
        assertThat(EntitlementService.asBoolean("anything")).isFalse();
    }

    @Test
    void asInt_parsesValidIntegers() {
        assertThat(EntitlementService.asInt("0", 99)).isEqualTo(0);
        assertThat(EntitlementService.asInt("42", 99)).isEqualTo(42);
        assertThat(EntitlementService.asInt("  7  ", 99)).isEqualTo(7);
        assertThat(EntitlementService.asInt("-3", 99)).isEqualTo(-3);
    }

    @Test
    void asInt_fallsBackForInvalidInput() {
        assertThat(EntitlementService.asInt(null, 5)).isEqualTo(5);
        assertThat(EntitlementService.asInt("", 5)).isEqualTo(5);
        assertThat(EntitlementService.asInt("   ", 5)).isEqualTo(5);
        assertThat(EntitlementService.asInt("not-a-number", 5)).isEqualTo(5);
        assertThat(EntitlementService.asInt("3.14", 5)).isEqualTo(5);
    }
}
