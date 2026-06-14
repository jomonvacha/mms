package com.roots.mms.entity;

/**
 * Built-in system roles.
 *
 * MEMBER is the standard business-facing user concept. Governance is expressed
 * through MODERATOR, MANAGER and ADMIN. USER is not a supported role — any
 * legacy ROLE_USER records are migrated to ROLE_MEMBER on startup by
 * {@code DataInitializer}.
 */
public enum ERole {
    ROLE_MEMBER,
    ROLE_MODERATOR,
    ROLE_ADMIN,
    ROLE_MANAGER
}
