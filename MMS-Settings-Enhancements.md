# MMS Settings & Account — Status (updated 2026-08-13)

*Originally captured 2026-05-28 as a benchmark against TradingView's settings UX.
Most of the backlog below has since shipped. This revision marks what's done,
keeps only the genuine remaining gaps, and records the defer decisions so this
file stops going stale.*

---

## Done since the original benchmark

| Item | Where it lives |
|---|---|
| Active sessions list + per-session revoke + revoke-all | `SessionController` (`GET`, `DELETE /{id}`, `DELETE`), `SessionService`, `UserSession` entity |
| Verified email-change flow (confirm link, old-address notice) | `UserController POST /me/email-change/request`, `AuthController GET /confirm-email-change`, `VerificationTokenService`, UI: `ConfirmEmailChange.tsx` |
| Self-service account deletion, reversible grace period | `UserController POST /me/deletion` (schedule) + `DELETE /me/deletion` (cancel), surfaced in `AccountModal.tsx` "Danger zone" |
| Regenerate 2FA backup/recovery codes | `TwoFactorController POST /recovery-codes` |
| Notification preference matrix (per-category × per-channel) | `UserPreferences.notificationPrefs` (jsonb), wired into `AccountModal.tsx` |
| Avatar upload limits | `UserController`, 2 MB cap enforced server-side |
| Username change with cooldown | `UserService`, cooldown configurable via `app.account.username-change-cooldown-days` (default 30d) |

The security-critical basics (password change, TOTP 2FA with hashed recovery
codes, OAuth, avatar, locale/theme prefs) plus the former "high value" gaps
(active sessions, email-change, account deletion) are all in place.

---

## Remaining backlog

1. **Subscription-state surface from Paddle** — *(Medium, deferred)*
   MMS owns the `MembershipType`/`MembershipTierConfig`/`TierEntitlement`
   entitlement model but has no payment-provider integration; no
   `Paddle`/`Subscription`/`Billing` code exists yet. Per the TradeCue plan,
   Paddle is merchant of record — it hosts checkout, stored cards, and
   invoices. MMS's job is only to reflect subscription *state*
   (active/cancelled/renewal) from Paddle webhooks, not build payment UI.
   **Decision: do not build this speculatively.** Pick it up when TradeCue's
   Paddle integration is actually ready to emit webhooks MMS can consume.

2. **3-group settings IA in `mms-ui`** — *(Low, UX polish)*
   `AccountModal.tsx` is a single ~1,150-line component holding profile,
   security (2FA, sessions), deletion, and notification preferences.
   Recommended split: *Profile & Privacy* / *Account & Security* /
   *Notifications* (billing folds into Account & Security until item 1 lands).

3. **SMS 2FA option** — *(Low / optional)*
   TOTP already covers the primary 2FA need; SMS is weaker and many products
   are de-emphasizing it. Only add if a specific user segment needs it.

4. **Subscriber status (pro/non-pro)** — *(Deferred to Phase 2)*
   Only relevant once TradeCue serves equities market data (regulatory
   data-licensing declaration). No action until then.

5. **Social links, presence/privacy toggles, blocking, social notifications** —
   *(Deferred)* MMS is a member-management backend, not a social product.
   Only worth building if a community layer is added.

---

*Keep this file in sync when backlog items ship — a stale "still missing" list
is worse than no list.*
