# MMS Settings & Account — Enhancement Notes (benchmarked vs TradingView)

*Date: 2026-05-28. Source: a read-only walkthrough of TradingView's user-settings pages (Public profile, Privacy preferences, Account settings) on 2026-05-28, benchmarked against the current MMS code (`mms-service`, package `com.roots.mms`). "MMS status" reflects what's actually in the codebase today.*

> Why TradingView: it's a mature, consumer-grade settings UX worth borrowing patterns from. Not every item applies to MMS — MMS is a member-management backend, so social/community items are optional and flagged as such.

---

## A. Public profile

| TradingView item | What it does | MMS status | Recommended enhancement |
|---|---|---|---|
| Profile **picture** | Upload photo; JPG/GIF/PNG, max **700 KB**, 4000px | **Has** — `UserController POST /api/users/me/avatar` (+ GET), `UserAvatar` entity | Confirm/enforce explicit size & dimension limits + allowed MIME types in the upload handler; surface them in the UI like TradingView does. |
| **Username** + Change username | Public handle, editable | **Has** — `User.username`, `PUT /api/users/me` | Add an explicit "change username" affordance + uniqueness/format validation messaging; consider a change-cooldown. |
| **Social & website links** | X, YouTube, Facebook, Instagram, Website | **Missing** | *Optional / only if MMS gains a public/community profile.* Add nullable, validated link fields (store as a small JSON or columns); validate scheme/host per network. Low priority for a pure member system. |

**IA pattern worth copying:** TradingView splits settings into a single dropdown selector — *Public profile · Privacy preferences · Account settings*. MMS's UI (`mms-ui`) currently has a preferences page; adopting this 3-group structure makes the settings area scannable.

---

## B. Privacy preferences

| TradingView item | What it does | MMS status | Recommended enhancement |
|---|---|---|---|
| **Anyone can see your online status** (toggle) | Presence/last-activity visibility | **Missing** | *Community feature.* Only relevant if MMS exposes presence. Skip unless a social layer is planned. |
| **Anyone can start a private chat** (toggle) | DM permission | **Missing** | *Community feature.* Skip unless MMS adds messaging. |
| **Blocked users list** (Show list) | Users who can't comment/message you | **Missing** | *Community feature.* Skip unless MMS adds social interactions. |
| **Language** selector | UI locale | **Has** — `UserPreferences.language` (+ theme, country, timezone, navbarDisplay) | None — already covered; ensure the UI selector is wired to this preference. |

**Takeaway:** the privacy section is almost entirely *social-graph* functionality. For MMS today these are **not** gaps — note them as "build only if a community/social dimension is added." The one real, already-met item is locale.

---

## C. Account settings  ← the high-value section for MMS

| TradingView item | What it does | MMS status | Recommended enhancement |
|---|---|---|---|
| **Change email** | Update sign-in email (with verification) | **Partial** — email lives on `User`; profile update exists | Add a **dedicated verified email-change flow**: require current password/2FA, email a confirmation link to the **new** address, and notify the **old** address. Don't allow a silent email swap via the profile PUT. |
| **Change password** | Update password | **Has** — `PUT /api/users/me/password`, `ChangePasswordRequest` | None functionally; ensure it invalidates other sessions / rotates refresh tokens on change. |
| **2FA — Authentication app (TOTP)** | Google Authenticator/Authy/Duo | **Has** — `/api/users/me/2fa` setup/enable/disable, `TwoFactorService` | None — solid (TOTP + verification on enable). |
| **2FA — Backup/recovery codes** + "Generate new codes" | One-time codes if phone is lost | **Has (issue) / Missing (regenerate)** — `TwoFactorService` issues 10 BCrypt-hashed recovery codes at enable, shown once | Add a **"regenerate backup codes" endpoint** (`POST /api/users/me/2fa/recovery-codes`) so users can mint fresh codes anytime, like TradingView — not only at first setup. |
| **2FA — Text message (SMS)** | OTP via SMS | **Missing** | *Optional.* Add SMS as a second 2FA method if desired (note: SMS is weaker than TOTP; many products now de-emphasize it). Lower priority than the regenerate-codes gap. |
| **Account deletion** (self-service, **30-day** reversible, can halt) | User deletes own account with grace period | **Missing** — only admin-side `Member` deactivate exists | **Add self-service account deletion** with a reversible grace window: mark `pending_deletion` + `deletionScheduledAt`, allow "halt," purge after N days. *For TradeCue specifically:* the flow must first **disable all bots and revoke/erase stored exchange credentials** before scheduling deletion. |

---

## E. Active sessions  ← high-value security section

TradingView shows **"Notifications about suspicious sign-ins"** (email alerts on unusual attempts) plus a **list of active sessions/devices** — each row: device + OS (e.g. "Mac, Mac OS X 10.15.7", "iPhone, iOS 18.7"), last-active date/time, city + country, IP address, and browser/app version, with a per-session **revoke**.

| Capability | MMS status | Recommended enhancement |
|---|---|---|
| List active sessions/devices | **Missing** — MMS has JWT + refresh tokens + `TokenBlacklistService`, but no session/device registry | Persist a **session record** per login (device/UA, OS, IP, geo, created/last-active, refresh-token id). Surface a list in settings. |
| Revoke a session remotely | **Partial** — blacklist exists for tokens | Add **per-session "sign out"** (revoke that refresh token + blacklist) and a **"sign out everywhere"**. |
| Suspicious-sign-in alerts | **Missing** | Email the user on a new device/geo/IP sign-in (reuse the existing Spring Mail setup). |

*High value — this is table-stakes account security and MMS doesn't have it yet.*

## F. Billing (Subscriptions · Payment methods · Billing history · Subscriber status)

What TradingView shows: **Subscriptions** (current plan / upgrade), **Payment methods** (saved card with billing address + delete; *disclaimer that TradingView does **not** store card data — it offloads to Checkout.com and Braintree*), **Billing history** (Date / Action / Transaction ID / Total table, per-row paid/cancelled status, copyable txn ids, **downloadable invoices**), and **Subscriber status** (a market-data regulatory classification — *Non-professional (private)* vs *Professional (commercial)* — set via a declaration form listing ~11 eligibility conditions). *(Personal card/address values seen on the account were intentionally not recorded here.)*

| Capability | MMS status | Recommended enhancement |
|---|---|---|
| Current plan + upgrade/cancel | **Partial** — MMS owns the `MembershipType`/`MembershipTierConfig`/`TierEntitlement` model (system of record for *what* a tier grants), but no end-user **subscription management** surface | Add a "Subscriptions" page showing current tier, renewal date, and cancel/upgrade. Drive state from the payment provider's webhooks. |
| Payment methods | **Missing** — and should stay so | **Do NOT store cards.** TradeCue uses **Paddle (merchant of record)**; delegate saved-cards to Paddle's customer portal (same PCI-offload pattern TradingView uses with Checkout.com/Braintree). MMS just links out. |
| Billing history / invoices | **Missing** | Show a transactions table with downloadable invoices — but source it from **Paddle** (MoR issues the invoices), optionally mirroring a read-only copy for display. |
| Subscriber status (pro/non-pro) | **Missing** | *Niche / equities-only.* Only relevant once TradeCue serves **equities market data**, where a professional/non-professional declaration affects data licensing. Capture as a user attribute + a declaration form (the ~11 conditions). Defer until Phase 2. |

**Key point for MMS/TradeCue:** because **Paddle is the merchant of record (decision in the TradeCue plan)**, most of this section is **delegated, not built** — Paddle hosts checkout, stored cards, and invoices. MMS keeps the *entitlement* truth and reflects subscription **state** (active/cancelled/renewal) from Paddle webhooks. This is cleaner than TradingView's self-hosted billing UI.

## G. Notifications (Alerts delivery · Social notifications · Email subscriptions)

*Could not open these three by URL (no guessable slugs) and the menu isn't clickable in read-only mode — documented at the design level; can be captured precisely if you click each item. Their purpose is clear from the labels:*

- **Alerts delivery** — channels for triggered alerts (email / SMS / push / popup / webhook) and per-channel toggles.
- **Social notifications** — likes/comments/follows/mentions preferences (community).
- **Email subscriptions** — granular marketing/newsletter opt-ins.

| Capability | MMS status | Recommended enhancement |
|---|---|---|
| Notification preference center | **Minimal** — `UserPreferences.emailNotifications` is a single boolean | Replace the single flag with a **per-category × per-channel matrix** (e.g. security, billing, execution/fills, product, marketing × email/SMS/push/webhook). |
| Marketing email opt-in/out | **Missing** | Add **granular, auditable email-subscription preferences** (needed for CAN-SPAM / GDPR compliance — esp. for the US/Florida-based entity). |
| Social notifications | **Missing** | Community-only; defer unless a social layer is added. |

For TradeCue specifically, "Alerts delivery" maps to **execution/fill notifications** (order placed/failed, reconciliation drift, setup warnings) — a real product need, not just marketing.

## D. Prioritized enhancement backlog (for MMS)

1. **Active sessions + remote revoke + suspicious-sign-in alerts** — table-stakes security, currently absent. *(High)*
2. **Verified email-change flow** — close the silent-swap gap. *(High)*
3. **Self-service account deletion with 30-day reversible grace** — required for consumer-facing TradeCue (must revoke exchange credentials + disable bots first). *(High)*
4. **Notification preference center** (per-category × per-channel) + auditable marketing opt-ins — replaces the single `emailNotifications` flag; compliance-relevant. *(High/Medium)*
5. **Subscription-state surface** (current tier / renewal / cancel) synced from **Paddle** webhooks — payment methods & invoices delegated to Paddle's portal, **not** built. *(Medium)*
6. **Regenerate backup codes endpoint/UI** — small add on existing TOTP. *(Medium)*
7. **Avatar limit enforcement + UI hints**; **username change** affordance + validation/cooldown. *(Medium)*
8. **3-group settings IA** in `mms-ui` (Profile & Privacy / Account & Security / Billing / Notifications). *(Low, UX polish)*
9. **SMS 2FA option.** *(Low / optional)*
10. **Subscriber status (pro/non-pro)** — only when TradeCue serves equities market data. *(Defer to Phase 2)*
11. **Social links + privacy/presence/blocking + social notifications** — only if MMS grows a community layer. *(Defer)*

**Net read:** MMS already matches TradingView on the security-critical basics (password change, TOTP 2FA **with** hashed recovery codes, OAuth, avatar, locale/theme prefs) — ahead of signum, which exposes no 2FA at all. The genuine, in-scope gaps cluster in **security/session management** (active sessions + revoke + sign-in alerts), **account lifecycle** (verified email change, self-service deletion), and **notification preferences**. **Billing is largely delegated to Paddle** (MoR) — MMS keeps the entitlement truth and reflects subscription state, rather than building payment/invoice UI. Privacy/social and subscriber-status items are out of scope until a community layer or equities, respectively.

*Captured read-only; no settings were changed on the TradingView account.*
