package com.roots.mms.entity;

/**
 * Built-in membership categories.
 *
 * Tiers are category-scoped (each category owns its own set of tier codes),
 * so this enum only identifies which category a member belongs to. The
 * runtime-editable state (display name, enabled flag, sort order) lives in
 * {@code MembershipCategoryConfig}.
 */
public enum MembershipCategory {
    PERSONAL,
    EDUCATION,
    ENTERPRISE
}
