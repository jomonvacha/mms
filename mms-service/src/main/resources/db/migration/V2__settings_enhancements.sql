-- MMS settings & account enhancements (benchmarked vs TradingView).
-- Covers the buildable backlog from MMS-Settings-Enhancements.md:
--   #1 active sessions + remote revoke
--   #2 verified email-change flow
--   #3 self-service account deletion with a reversible grace window
--   #4 notification preference center
--   #7 username-change cooldown
-- Conventions mirror V1: UUID PKs, TIMESTAMPTZ for event-like timestamps,
-- JSONB only for genuinely variable-shape payloads.

-- ─────────────────────────────────────────────────────────────
-- #1 Active sessions / device registry
-- ─────────────────────────────────────────────────────────────
-- One row per sign-in. The row id IS the session id (the `sid` claim embedded
-- in both access and refresh JWTs), so a presented token maps back to its
-- session for remote revoke and "current session" detection.

CREATE TABLE user_sessions (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    device_label    VARCHAR(128),
    user_agent      VARCHAR(512),
    ip              VARCHAR(64),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMPTZ  NOT NULL,
    revoked_at      TIMESTAMPTZ
);

CREATE INDEX idx_user_sessions_user_id    ON user_sessions (user_id);
CREATE INDEX idx_user_sessions_expires_at ON user_sessions (expires_at);

-- ─────────────────────────────────────────────────────────────
-- #2 Verified email-change flow
-- ─────────────────────────────────────────────────────────────
-- Reuse the verification_tokens table: add an EMAIL_CHANGE type and a column
-- holding the pending new address (only populated for EMAIL_CHANGE rows).

ALTER TABLE verification_tokens DROP CONSTRAINT verification_tokens_type_check;
ALTER TABLE verification_tokens ADD  CONSTRAINT verification_tokens_type_check
    CHECK (type IN ('PASSWORD_RESET','EMAIL_VERIFICATION','EMAIL_CHANGE'));
ALTER TABLE verification_tokens ADD COLUMN new_email VARCHAR(254);

-- ─────────────────────────────────────────────────────────────
-- #3 Self-service account deletion (reversible grace window)
-- #7 Username-change cooldown
-- ─────────────────────────────────────────────────────────────

ALTER TABLE users ADD COLUMN pending_deletion      BOOLEAN     NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN deletion_scheduled_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN username_changed_at   TIMESTAMPTZ;

CREATE INDEX idx_users_pending_deletion ON users (deletion_scheduled_at)
    WHERE pending_deletion IS TRUE;

-- ─────────────────────────────────────────────────────────────
-- #4 Notification preference center
-- ─────────────────────────────────────────────────────────────
-- Per-category x per-channel matrix, variable shape, stored as JSONB.
-- The legacy email_notifications boolean is retained for back-compat.

ALTER TABLE user_preferences ADD COLUMN notification_prefs JSONB;
