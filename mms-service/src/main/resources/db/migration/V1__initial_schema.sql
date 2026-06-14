-- MMS initial PostgreSQL schema (Neon-compatible).
-- Replaces the MongoDB document model documented in docs/mms-postgres-migration-analysis.md.
-- Conventions:
--   * UUID primary keys (pgcrypto gen_random_uuid()) — mapped from Mongo ObjectIds at migrate time.
--   * Enums stored as VARCHAR + CHECK constraints (JPA @Enumerated(EnumType.STRING)).
--   * Business codes (category_code, tier_code, entitlement_key, model_code, role name) stay VARCHAR
--     and remain stable across the migration. They are the real join keys.
--   * JSONB only for genuinely variable-shape payloads (audit snapshots, recovery code hashes).
--   * Timestamps are TIMESTAMPTZ for anything event-like, TIMESTAMP for pure row audit fields
--     that mirrored LocalDateTime in the Mongo model.
--   * Every unique/compound index from the Mongo model is re-declared here.

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ─────────────────────────────────────────────────────────────
-- Identity & access: users, roles, user_roles
-- ─────────────────────────────────────────────────────────────

CREATE TABLE roles (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(64) NOT NULL UNIQUE,
    display_name    VARCHAR(128),
    description     TEXT,
    category        VARCHAR(64) NOT NULL DEFAULT 'System',
    system          BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE TABLE users (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    username         VARCHAR(20)  NOT NULL UNIQUE,
    email            VARCHAR(254) NOT NULL UNIQUE,
    password         VARCHAR(120),
    first_name       VARCHAR(50)  NOT NULL,
    last_name        VARCHAR(50)  NOT NULL,
    phone_number     VARCHAR(15),
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    provider         VARCHAR(16)  NOT NULL DEFAULT 'LOCAL'
                        CHECK (provider IN ('LOCAL','GOOGLE','APPLE')),
    provider_id      VARCHAR(128),
    email_verified   BOOLEAN      NOT NULL DEFAULT FALSE,
    totp_secret      VARCHAR(128),
    totp_enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    totp_recovery_codes JSONB,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_provider_providerid ON users (provider, provider_id) WHERE provider_id IS NOT NULL;

CREATE TABLE user_roles (
    user_id         UUID NOT NULL REFERENCES users (id)  ON DELETE CASCADE,
    role_id         UUID NOT NULL REFERENCES roles (id) ON DELETE RESTRICT,
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_role_id ON user_roles (role_id);

-- ─────────────────────────────────────────────────────────────
-- Features & role-feature matrix
-- ─────────────────────────────────────────────────────────────

CREATE TABLE features (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(64) NOT NULL UNIQUE,
    name        VARCHAR(128),
    description TEXT,
    category    VARCHAR(64),
    icon        VARCHAR(64),
    enabled     BOOLEAN     NOT NULL DEFAULT TRUE
);

-- Mongo had a single `role_features` collection keyed by role with a Set<String>
-- of feature codes. JPA requires a split: a parent `role_feature_maps` keyed by
-- UUID (plus a unique role name), and a child `role_features` holding the set
-- via @ElementCollection. Business keys (role name, feature code) remain stable.
CREATE TABLE role_feature_maps (
    id        UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    role_name VARCHAR(64) NOT NULL UNIQUE
                  REFERENCES roles (name) ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE role_features (
    role_feature_map_id UUID        NOT NULL
                            REFERENCES role_feature_maps (id) ON DELETE CASCADE,
    feature_code        VARCHAR(64) NOT NULL
                            REFERENCES features (code) ON UPDATE CASCADE ON DELETE CASCADE,
    PRIMARY KEY (role_feature_map_id, feature_code)
);

CREATE INDEX idx_role_features_feature_code ON role_features (feature_code);

-- ─────────────────────────────────────────────────────────────
-- Governance: membership categories, tiers, types
-- ─────────────────────────────────────────────────────────────

CREATE TABLE membership_categories (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    code          VARCHAR(32) NOT NULL UNIQUE,
    display_name  VARCHAR(128),
    description   TEXT,
    enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order    INTEGER     NOT NULL DEFAULT 0,
    system        BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE TABLE membership_tiers (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    category_code VARCHAR(32) NOT NULL REFERENCES membership_categories (code) ON UPDATE CASCADE,
    tier_code     VARCHAR(32) NOT NULL,
    display_name  VARCHAR(128),
    description   TEXT,
    enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order    INTEGER     NOT NULL DEFAULT 0,
    system        BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_membership_tiers_cat_tier UNIQUE (category_code, tier_code)
);

CREATE INDEX idx_membership_tiers_category ON membership_tiers (category_code);

-- Legacy enum catalog (BASIC/PREMIUM/...); kept as extensible lookup.
CREATE TABLE membership_types (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    code         VARCHAR(32) NOT NULL UNIQUE,
    display_name VARCHAR(128),
    description  TEXT,
    system       BOOLEAN     NOT NULL DEFAULT FALSE
);

-- ─────────────────────────────────────────────────────────────
-- Entitlements & tier entitlement matrix
-- ─────────────────────────────────────────────────────────────

CREATE TABLE entitlements (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    key           VARCHAR(128) NOT NULL UNIQUE,
    display_name  VARCHAR(256),
    description   TEXT,
    value_type    VARCHAR(16) NOT NULL DEFAULT 'BOOLEAN'
                    CHECK (value_type IN ('BOOLEAN','INTEGER','STRING')),
    category      VARCHAR(64),
    default_value VARCHAR(256) NOT NULL DEFAULT 'false',
    system        BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE TABLE tier_entitlements (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    category_code   VARCHAR(32)  NOT NULL,
    tier_code       VARCHAR(32)  NOT NULL,
    entitlement_key VARCHAR(128) NOT NULL REFERENCES entitlements (key) ON UPDATE CASCADE ON DELETE CASCADE,
    value           VARCHAR(512),
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tier_entitlements UNIQUE (category_code, tier_code, entitlement_key),
    CONSTRAINT fk_tier_entitlements_tier FOREIGN KEY (category_code, tier_code)
        REFERENCES membership_tiers (category_code, tier_code) ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE INDEX idx_tier_entitlements_entitlement_key ON tier_entitlements (entitlement_key);

-- ─────────────────────────────────────────────────────────────
-- Members (one per user, category/tier assignment)
-- ─────────────────────────────────────────────────────────────

CREATE TABLE members (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    membership_id        VARCHAR(50)  NOT NULL UNIQUE,
    user_id              UUID         NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    membership_type      VARCHAR(32)  NOT NULL
                             CHECK (membership_type IN
                                ('BASIC','PREMIUM','VIP','CORPORATE','STUDENT','SENIOR')),
    category_code        VARCHAR(32)  REFERENCES membership_categories (code) ON UPDATE CASCADE,
    tier_code            VARCHAR(32),
    status               VARCHAR(16)  NOT NULL
                             CHECK (status IN
                                ('ACTIVE','INACTIVE','SUSPENDED','EXPIRED','PENDING','CANCELLED')),
    membership_start_date DATE,
    membership_end_date   DATE,
    notes                VARCHAR(500),
    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,

    -- Denormalized user snapshot (mirrors Mongo UserSummary).
    -- Kept to preserve MemberRepository.findByKeyword() semantics without refactoring the service layer.
    -- Sync points live in MemberService.syncDenormalizedUserSnapshot().
    user_snapshot_username    VARCHAR(20),
    user_snapshot_email       VARCHAR(254),
    user_snapshot_first_name  VARCHAR(50),
    user_snapshot_last_name   VARCHAR(50),
    user_snapshot_phone       VARCHAR(15),
    user_snapshot_active      BOOLEAN,

    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_members_tier FOREIGN KEY (category_code, tier_code)
        REFERENCES membership_tiers (category_code, tier_code) ON UPDATE CASCADE ON DELETE SET NULL
);

CREATE INDEX idx_members_user_id          ON members (user_id);
CREATE INDEX idx_members_membership_type  ON members (membership_type);
CREATE INDEX idx_members_category_code    ON members (category_code);
CREATE INDEX idx_members_tier_code        ON members (tier_code);
CREATE INDEX idx_members_status           ON members (status);

-- Trigram indexes back the MemberRepository.findByKeyword() ILIKE '%x%' query.
CREATE INDEX idx_members_snap_email_trgm      ON members USING gin (user_snapshot_email      gin_trgm_ops);
CREATE INDEX idx_members_snap_first_name_trgm ON members USING gin (user_snapshot_first_name gin_trgm_ops);
CREATE INDEX idx_members_snap_last_name_trgm  ON members USING gin (user_snapshot_last_name  gin_trgm_ops);
CREATE INDEX idx_members_membership_id_trgm   ON members USING gin (membership_id            gin_trgm_ops);

-- ─────────────────────────────────────────────────────────────
-- AI model registry & tier bindings
-- ─────────────────────────────────────────────────────────────

CREATE TABLE ai_model_providers (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    code         VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(128),
    base_url     VARCHAR(512),
    enabled      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ai_models (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    code                 VARCHAR(64) NOT NULL UNIQUE,
    provider_code        VARCHAR(64) NOT NULL REFERENCES ai_model_providers (code) ON UPDATE CASCADE,
    display_name         VARCHAR(128),
    description          TEXT,
    quality_level        INTEGER     NOT NULL DEFAULT 2 CHECK (quality_level BETWEEN 1 AND 4),
    cost_level           INTEGER     NOT NULL DEFAULT 2 CHECK (cost_level    BETWEEN 1 AND 4),
    status               VARCHAR(16) NOT NULL DEFAULT 'ENABLED'
                            CHECK (status IN ('ENABLED','DISABLED','DEPRECATED')),
    lock_style           VARCHAR(8)  NOT NULL DEFAULT 'LOCK'
                            CHECK (lock_style IN ('LOCK','HIDE')),
    deprecated_at        TIMESTAMPTZ,
    replaces_model_code  VARCHAR(64),
    sort_order           INTEGER     NOT NULL DEFAULT 100,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by           VARCHAR(64),
    updated_by           VARCHAR(64)
);

CREATE INDEX idx_ai_models_provider ON ai_models (provider_code);

CREATE TABLE ai_model_tier_bindings (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    model_code      VARCHAR(64) NOT NULL REFERENCES ai_models (code) ON UPDATE CASCADE ON DELETE CASCADE,
    category_code   VARCHAR(32) NOT NULL,
    tier_code       VARCHAR(32) NOT NULL,
    is_default      BOOLEAN     NOT NULL DEFAULT FALSE,
    label_override  VARCHAR(128),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(64),
    updated_by      VARCHAR(64),
    CONSTRAINT uq_ai_model_tier_binding UNIQUE (model_code, category_code, tier_code),
    CONSTRAINT fk_ai_model_tier FOREIGN KEY (category_code, tier_code)
        REFERENCES membership_tiers (category_code, tier_code) ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE INDEX idx_ai_model_tier_bindings_category_tier ON ai_model_tier_bindings (category_code, tier_code);

-- At most one default model per (category, tier).
CREATE UNIQUE INDEX uq_ai_model_tier_default
    ON ai_model_tier_bindings (category_code, tier_code)
    WHERE is_default IS TRUE;

-- ─────────────────────────────────────────────────────────────
-- AI model audit log (1-year retention via scheduled job; replaces Mongo TTL)
-- ─────────────────────────────────────────────────────────────

CREATE TABLE ai_model_audit (
    id                     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id               VARCHAR(128),
    action                 VARCHAR(48) NOT NULL
                               CHECK (action IN (
                                  'PROVIDER_CREATED','PROVIDER_UPDATED','PROVIDER_DELETED',
                                  'MODEL_CREATED','MODEL_UPDATED','MODEL_STATUS_CHANGED','MODEL_DELETED',
                                  'BINDING_CREATED','BINDING_UPDATED','BINDING_DELETED')),
    model_code             VARCHAR(64),
    binding_category_code  VARCHAR(32),
    binding_tier_code      VARCHAR(32),
    before_json            JSONB,
    after_json             JSONB,
    ip                     VARCHAR(64),
    user_agent             VARCHAR(512),
    at                     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ai_model_audit_actor_id  ON ai_model_audit (actor_id);
CREATE INDEX idx_ai_model_audit_action    ON ai_model_audit (action);
CREATE INDEX idx_ai_model_audit_model_code ON ai_model_audit (model_code);
CREATE INDEX idx_ai_model_audit_at_desc   ON ai_model_audit (at DESC);

-- ─────────────────────────────────────────────────────────────
-- Enforcement rules (AI prompt injections)
-- ─────────────────────────────────────────────────────────────

CREATE TABLE enforcement_rules (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    text                TEXT         NOT NULL,
    tier                VARCHAR(16)  NOT NULL CHECK (tier IN ('SYSTEM','OPTIONAL')),
    category            VARCHAR(64),
    enabled_by_default  BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order          INTEGER      NOT NULL DEFAULT 0,
    active              BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_enforcement_rules_tier ON enforcement_rules (tier);

-- ─────────────────────────────────────────────────────────────
-- Verification tokens (password reset / email verify). Hourly sweep replaces Mongo TTL.
-- ─────────────────────────────────────────────────────────────

CREATE TABLE verification_tokens (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    token        VARCHAR(128) NOT NULL UNIQUE,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type         VARCHAR(32) NOT NULL
                    CHECK (type IN ('PASSWORD_RESET','EMAIL_VERIFICATION')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at   TIMESTAMPTZ NOT NULL,
    consumed_at  TIMESTAMPTZ
);

CREATE INDEX idx_verification_tokens_user_id    ON verification_tokens (user_id);
CREATE INDEX idx_verification_tokens_expires_at ON verification_tokens (expires_at);

-- ─────────────────────────────────────────────────────────────
-- Signup invite codes
-- ─────────────────────────────────────────────────────────────

CREATE TABLE signup_invite_codes (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(64)  NOT NULL UNIQUE,
    description TEXT,
    max_uses    INTEGER,
    used_count  INTEGER      NOT NULL DEFAULT 0,
    expires_at  TIMESTAMPTZ,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by  VARCHAR(128),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ─────────────────────────────────────────────────────────────
-- Platform configuration & locale options
-- ─────────────────────────────────────────────────────────────

CREATE TABLE app_settings (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    key         VARCHAR(128) NOT NULL UNIQUE,
    value       TEXT,
    description TEXT,
    updated_by  VARCHAR(128),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE locale_options (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    type       VARCHAR(32)  NOT NULL CHECK (type IN ('LANGUAGE','COUNTRY','TIMEZONE')),
    code       VARCHAR(64)  NOT NULL,
    label      VARCHAR(128),
    sort_order INTEGER      NOT NULL DEFAULT 0,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_locale_options UNIQUE (type, code)
);

CREATE INDEX idx_locale_options_type ON locale_options (type);

-- ─────────────────────────────────────────────────────────────
-- User profile: preferences & avatar (1:1 with users)
-- ─────────────────────────────────────────────────────────────

CREATE TABLE user_preferences (
    user_id             UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    theme               VARCHAR(16)  NOT NULL DEFAULT 'system',
    language            VARCHAR(16)  NOT NULL DEFAULT 'en',
    country             VARCHAR(32),
    timezone            VARCHAR(64),
    email_notifications BOOLEAN      NOT NULL DEFAULT TRUE,
    navbar_display      VARCHAR(16)  NOT NULL DEFAULT 'avatar'
);

CREATE TABLE user_avatars (
    user_id      UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    content_type VARCHAR(64) NOT NULL,
    data         BYTEA       NOT NULL
);
