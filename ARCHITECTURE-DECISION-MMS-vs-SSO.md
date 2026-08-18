# MMS vs SSO — Architecture Analysis & Decision Record

*Status: DECISIONS MADE, implementation not yet approved. No code has been
changed as part of this analysis. Written 2026-08-13, updated 2026-08-13
with resolved decisions + SSO readiness audit, updated 2026-08-14 revising
the tenant model from one shared `Organization` to configurable
per-product grouping (§7 item 1), updated 2026-08-17 to correct §8/§9
against verified current state: `sso` is now genuinely live in production
(Google Cloud Run, not the GKE/k8s path this doc originally assumed — see
§8 Phase 0 items 1 and 4, and §9 gaps 1–2). This is a real, material
correction, not a status bump — it changes how close Phase 0 actually is
to done.*

## TL;DR

- **`mms` and `sso` are not the same system and don't compete for the same
  job.** `sso` is a generic, multi-tenant OAuth2/OIDC Authorization Server
  (identity only). `mms` is a single-tenant member-management backend that
  bundles identity *and* product-specific business logic (membership tiers,
  entitlements, AI model governance) behind one JWT.
- **`sso` is currently unused by any other product** — no code in `mms`,
  `IDFY`, or `TradeCue` references it as an identity provider. This is
  still a green-field decision, not a migration of an existing plan.
  (`sso` itself, however, is no longer a dev-machine-only project — it now
  runs live in two real Cloud Run environments with real traffic; see
  below and §9. Nobody depends on it for login yet, but it is not
  hypothetical infrastructure anymore.)
- Two real problems exist in the **current** MMS-centered architecture,
  independent of any SSO decision:
  1. **IDFY embeds a stale, drifted fork of `mms-service`** (13 files
     behind the standalone repo — missing sessions, account deletion, email
     change, notification prefs).
  2. **TradeCue is wired to validate MMS JWTs via JWKS, but MMS has no JWKS
     endpoint.** This isn't hypothetical — `MMS_JWKS_URI` is a real
     production deploy secret in TradeCue's CI. As built today, this
     integration does not work.
- **Decision: Option C (hybrid)** — keep MMS as the system of record for
  membership/entitlements, but stop having it also be a password/2FA/session
  identity provider. `sso` will own identity for the whole product family
  (IDFY, TradeCue, familytree/roots, plus MMS's own admin login); MMS
  becomes an OAuth2 resource server that trusts `sso`-issued tokens. See §5.
- **This is a planned migration, not an urgent fix** — confirmed, no live
  incident is forcing this (§7, item 4).
- **Blocking gate before any product depends on `sso` for login: `sso` is
  further along than this doc previously gave it credit for, but Phase 0
  isn't done.** §9 has the full gap list; as of 2026-08-17 five of eight
  Phase 0 items are closed: secret management (Cloud KMS + Secret Manager,
  live in production — the `k8s`/External Secrets Operator path this doc
  originally described was abandoned in favor of Cloud Run, see §8 item 1),
  a real CD pipeline with two live environments (`shared` and `production`
  on Google Cloud Run — this reverses the previous "never deployed outside
  a dev machine" finding, see §8 item 4 and §9 gap 2), WebAuthn/
  adaptive-auth test coverage, the ZAP scan, and `mvn verify`. Frontend
  coverage is raised but deliberately not at a hard 50% (auth-critical
  files only). **Still open**: a real load-test baseline against the now-live
  `shared` environment, a completed burn-in period (traffic only started
  2026-08-15 — a couple of days in, not the proposed 2–4 weeks), and
  tenant-scoped self-service signup (confirmed still missing as of
  2026-08-17 — `PublicController.signup` still only creates brand-new
  `Organization`s). These three close out Phase 0.

---

## 1. What each system actually is

### `mms` — Member Management System
Spring Boot 4 / Java 25, PostgreSQL. A single deployable that combines two
distinct concerns in one codebase:

- **Identity**: `User`, username/password + TOTP 2FA (QR + recovery codes),
  `UserSession` (device/IP list + revoke), email verification, verified
  email-change flow, self-service account deletion with grace period, Google
  OAuth2 login (`oauth2Login`, consumer-side).
- **Business domain** (this is the part `sso` has no concept of at all):
  `Member`, `MembershipCategory`/`MembershipTierConfig` (PERSONAL/EDUCATION/
  ENTERPRISE × FREE/PRO/MAX), `Entitlement`/`TierEntitlement` (per-tier
  feature limits — max personas, knowledge-source caps, API access, etc.),
  `AiModel`/`AiModelTierBinding` (which AI models a tier can use),
  `Feature`/`RoleFeatureMap` (MMS's own admin-UI page gating).

JWTs are signed with a **symmetric HMAC secret** (`Keys.hmacShaKeyFor(...)`,
confirmed in `mms-service/src/main/java/com/roots/mms/security/jwt/JwtUtils.java`).
No JWKS endpoint, no OIDC discovery document. Any service that wants to
validate an MMS token needs the literal `JWT_SECRET` value.

### `sso` — OIDC/OAuth2 Authorization Server
Spring Boot 4 / Java 25 + Spring Authorization Server, PostgreSQL, React 19
frontend. A generic, **multi-tenant** identity provider:

- `Organization` (tenant isolation), `ClientApp` (OAuth2 client registration
  — this is the mechanism by which other apps become relying parties),
  `Role`/`Group`/`AccessPolicy` (RBAC/ABAC scoped per org+client).
- MFA: TOTP, **email OTP, and WebAuthn/passkeys** (MMS only has TOTP).
- **Adaptive authentication** — step-up challenge on new device/IP (MMS has
  none).
- `JwkKey` with rotation — asymmetric signing, **JWKS and OIDC discovery
  endpoints ship out of the box** via Spring Authorization Server
  (`AuthorizationServerConfig.java`, `issuer-uri` configured in
  `application.yml:149`).
- Refresh-token rotation with reuse detection, online revocation, per-IP/
  per-credential rate limiting, encrypted-at-rest secrets (AES-GCM), GDPR
  export/erasure endpoints, full audit log, admin **and developer** portals,
  OpenTelemetry/Prometheus/Grafana/Jaeger observability stack.

Materially more mature *design* than MMS's auth layer on every axis of
"identity provider" — but see §9: design maturity and production readiness
are not the same thing, and `sso` is not yet the latter.

### The honest comparison

| Capability | MMS | SSO |
|---|---|---|
| Password auth | ✅ | ✅ |
| 2FA: TOTP | ✅ | ✅ |
| 2FA: Email OTP | ❌ | ✅ |
| 2FA: WebAuthn/passkeys | ❌ | ✅ (untested — see §9) |
| Adaptive / step-up auth | ❌ | ✅ (untested — see §9) |
| Multi-tenant orgs | ❌ (single deployment = single tenant) | ✅ |
| OAuth2 client registry (for RPs) | ❌ | ✅ (`ClientApp`) |
| Token signing | Symmetric HMAC, one shared secret | Asymmetric, JWKS + rotation |
| OIDC discovery | ❌ | ✅ |
| Session list + revoke | ✅ | ✅ |
| Refresh-token reuse detection | ❌ (blacklist only) | ✅ |
| Audit log | Partial | ✅ Full |
| GDPR export/erasure | Partial (deletion only) | ✅ |
| Rate limiting / lockouts | Partial | ✅ |
| Membership tiers / entitlements | ✅ (core purpose) | ❌ N/A |
| AI model governance per tier | ✅ (core purpose) | ❌ N/A |
| Consumer Google OAuth2 login | ✅ | not checked (likely, out of scope here) |
| **Production-proven** | ✅ (running today for 3 products) | ❌ (never deployed outside a dev machine — §9) |

**Reading this table**: everything in the "Identity" rows is a case of MMS
having built a smaller, less mature *design* than `sso`. But the last row
matters just as much: MMS is boring and it works; `sso` is better-designed
and unproven. The migration plan (§8) has to respect that gap, not paper
over it.

---

## 2. Current integration reality (as-built)

### IDFY → embeds a stale fork of mms-service
- `idfy-platform`'s root `pom.xml` lists `idfy-service` and `mms-service` as
  sibling Maven modules — **no dependency between them**, they build and
  deploy as two separate JARs/containers.
- `idfy-service` has **no local user table**. Its `SecurityConfig.java` is
  explicitly commented "Validates tokens issued by roots-mms (shared JWT
  secret). No user/role lookup — just token validation + subject
  extraction." Same `JWT_SECRET` env var required on both services.
- `idfy-service` calls MMS over HTTP for models and entitlements
  (`MmsModelClient`, `MmsEntitlementsClient`, defaulting to
  `http://localhost:8081`).
- **The `mms-service` folder inside `idfy-platform` is a vendored copy of
  this standalone `mms` repo's code** — same package (`com.roots.mms`),
  `DataInitializer.java` byte-identical — but it is **13 files behind**:
  missing `SessionController`/`SessionService`/`UserSession`,
  `InternalMailController`, account-deletion (`AccountDeletionRequest`,
  `AccountDeletionJob`), email-change (`ChangeEmailRequest`), and
  `NotificationPreferences`. No `.gitmodules`, no nested `.git` — it's a
  plain copy-paste, not a tracked dependency. **Confirmed (§7 item 3): no
  in-process usage** — `idfy-service` only ever talks to it over HTTP, so
  it's safe to delete outright once the HTTP-only pattern is in place.

### TradeCue → external consumer, but the integration contract is broken
- `tradecue-service` has **no local user/member entity at all** —
  `BillingAccount` stores only a `userId UUID` pointing at an MMS user.
- It's configured as a pure **OAuth2 resource server**:
  `spring.security.oauth2.resourceserver.jwt.jwk-set-uri: ${MMS_JWKS_URI:}`
  and `issuer-uri: ${MMS_ISSUER_URI:}` (`application.yml:36-37`). Both
  default to empty string — nothing overrides this with a custom
  HMAC-secret `JwtDecoder` bean in `SecurityConfig.java`.
- **MMS has no JWKS endpoint and no OIDC discovery document.** It can't,
  as currently built — it signs with a symmetric secret, and there is
  nothing public to serve. So `MMS_JWKS_URI` cannot point at anything that
  exists today.
- This isn't a stale doc or aspirational comment — `MMS_JWKS_URI` is listed
  as a real secret in TradeCue's GCP deploy workflow
  (`.github/workflows/deploy.yml`). Someone planned for MMS to serve JWKS
  and it doesn't. **Confirmed (§7 item 4): this is not a live incident** —
  the gap has simply never been exercised in production yet, so this is a
  planned migration, not a fire drill.
- `EntitlementClient.java` (TradeCue) separately calls MMS's
  `/api/users/me/entitlements` over plain HTTP with the forwarded bearer
  token — that half of the integration is real and functional, independent
  of the JWKS question.
- TradeCue also has its own Paddle billing integration
  (`PaddleWebhookController` etc.) — unrelated to MMS, already correctly
  scoped as TradeCue's own concern.

### SSO → not integrated anywhere
No file in `mms`, `IDFY`, or `TradeCue` references the `sso` project, an
OIDC discovery URL, or `oauth2Login` pointed at a custom authorization
server. `mms`'s existing `oauth2Login` is consumer Google sign-in, unrelated
to the in-house `sso` project.

### Familytree/roots → a third integration pattern, also stale
Two checkouts exist: `/Users/jomonvacha/Projects/my-roots-project` is the
active one (matches its git remote, has local work); `familytree-project`
is a clean duplicate clone pinned to the same old commit (`63386dd`,
2026-06-14) — same "stale parallel copy" smell as IDFY's vendored MMS, just
one level up (a whole extra checkout of the roots repo itself, not just
mms-service). Worth a cleanup pass independent of this migration.

Findings from `my-roots-project`:
- **`mms-service` is vendored here too** (sibling Maven module, same
  pattern as IDFY), and **also stale** — 120 files vs. the standalone
  repo's 212, missing the same set IDFY is missing (`SessionController`,
  `InternalMailController`, `NotificationPreferences`, account-deletion and
  email-change code). Frozen at 2026-06-14, roughly two months behind.
- **`roots-service` itself uses a third, cleaner integration pattern**:
  **token introspection**, not shared-secret local validation (IDFY) or
  JWKS (TradeCue, broken). It has no `User` entity, no `JWT_SECRET`, no
  login controller — instead `IntrospectionClient.java` calls MMS's
  `POST /api/auth/introspect` on every request (30s Caffeine-cached, fails
  closed on error). The `application.yml` comment says it plainly:
  *"roots-service holds no JWT secret — it asks MMS."* This is the most
  decoupled of the three current patterns and translates cleanly to `sso`
  later (see §8 Phase 4).
- **`roots-ui`** is a fully separate frontend from `mms-ui` (own
  `SignIn`/`SignUp`/`AuthProvider`), but its dev proxy forwards `/mms` and
  `/oauth2` straight to the MMS backend — same login flow as standalone
  MMS, just a second UI implementation of it rather than a shared
  component.

---

## 3. Direct answer to "is MMS the same as SSO?"

No. They sit at different layers:

```
 sso   = WHO is this user?          (identity, generic, multi-tenant)
 mms   = WHAT can this user do,     (membership tier, entitlements,
         and what product-specific   AI model access — specific to the
         data do they have?          IDFY/TradeCue/roots family)
```

The overlap is narrow and shallow: both have "a User row, a password, a
JWT." Everything MMS does *beyond* that (memberships, tiers, entitlements,
AI model registry) has no equivalent in `sso` and never should — that's
not an identity-provider concern.

---

## 4. Options considered

### Option A — Status quo, patched
Keep MMS as the shared identity + entitlement backend. Fix the two bugs in
place (delete IDFY's vendored copy; give MMS a real JWKS endpoint via
asymmetric signing). Smallest change, but keeps re-implementing a weaker
identity provider than the one that already exists in `sso`.

### Option B — Full migration: SSO becomes the only identity provider
Every product becomes an OAuth2 client + resource server of `sso`. MMS's
own `User`/password/2FA/session code is deleted entirely; MMS keeps only
`Member`/tier/entitlement data keyed off `sso`'s `sub` claim. Cleanest end
state, but throws away MMS's working identity code before `sso` has proven
itself in production (see §9) — no fallback if `sso` isn't ready.

### Option C — Hybrid: SSO owns identity, MMS owns membership (decided)
Same end state as Option B for *identity*, but MMS is not deleted — it
keeps `Member`/tiers/entitlements/AI model governance and becomes a
resource server trusting `sso` tokens, same pattern IDFY and TradeCue will
also use. MMS stops owning `User`/password/session tables.

---

## 5. Decision: Option C

Confirmed. Reasoning stands from the original analysis:

- MMS's membership/entitlement/AI-model-governance logic is real, working,
  actively used — not up for replacement.
- MMS's *identity* code is a strictly smaller, less secure subset of what
  `sso` already does. No reason to keep hardening it once `sso` is real.
- TradeCue's architecture already assumes "validate JWT via JWKS, then call
  MMS for entitlements" — pointing that at `sso`'s already-working JWKS is
  less total work than building JWKS into MMS from scratch.
- IDFY's stale vendored copy is a symptom of MMS owning something (identity)
  it shouldn't own long-term.

---

## 6. Familytree/roots project

**Decision (confirmed): included in the same migration wave as IDFY and
TradeCue.** Audited (§2) — it has both of the problems already found
elsewhere, plus a third integration pattern worth reusing:

- Its vendored `mms-service` copy has the identical staleness problem as
  IDFY's (missing the same 5+ files, frozen ~2 months behind) — same fix,
  delete it once `roots-service` no longer needs anything from it.
- `roots-service`'s **token-introspection** pattern (`IntrospectionClient.java`
  calling MMS's `POST /api/auth/introspect`, no local secret) is the
  cleanest of the three patterns found across all products. It requires no
  shared secret and no local JWT decoding — it just asks the issuer "is
  this token good, who is it." This maps directly onto standard OAuth2
  token introspection (RFC 7662), which Spring Authorization Server exposes
  by default (needs confirming for this specific `sso` config during Phase
  4 planning, but is not a new capability to build). **This is the
  reference pattern for Phase 4**, not JWKS — simpler to reason about than
  asymmetric key validation for services that don't need to decode claims
  locally.
- `roots-ui` reimplements MMS's login flow in its own frontend rather than
  reusing `mms-ui` — after the migration this becomes an OIDC redirect to
  `sso`, same as every other product's frontend (Phase 3/4).

---

## 7. Resolved decisions

All open questions from the original draft are now answered:

1. **Tenant model — configurable per-product, isolate by default;
   `ClientApp`-level separation within any group that shares an org.**
   Reasoning, checked directly against `sso`'s schema:
   - `UserAccount` is hard-scoped 1:1 to `Organization`
     (`domain/UserAccount.java`) — a user can only belong to one org, so
     "shared identity" and "isolated" are mutually exclusive per pairing of
     products, not a spectrum you can partially pick.
   - `Role` is per-`Organization`, `AccessPolicy` (MFA-on-new-device, geo
     allow/block) is 1:1 per `Organization`, and admin visibility is
     enforced at the org boundary (`tenantGuard.requireSameOrg`, checked in
     every `AdminController` endpoint) — there is no `ClientApp`-level
     admin scoping today. Sharing an `Organization` therefore means sharing
     identity, role namespace, security policy, *and* admin visibility all
     at once; there is no mechanism to share just one of those.
   - `ClientApp.allowedRoles` (a `@ManyToMany` join to `Role`) is the
     mechanism for separating populations *within* a shared org — it
     restricts which roles a token minted for that client can carry,
     without touching identity, policy, or admin visibility.

   **Decision rule**: default every new product to its own `Organization`.
   Only put two products in the same `Organization` when there's an
   explicit, deliberate reason, gated by:
   1. Does this product need its own security posture (MFA/geo policy)? →
      isolate.
   2. Should its users be invisible to another product's admins? →
      isolate.
   3. Do the *same humans* genuinely need one login across both products? →
      only then, share.

   **Current grouping**, per this rule:

   | Product | Organization | Rationale |
   |---|---|---|
   | MMS | `shared-org` | shared identity with TradeCue — real product need, same users work across both |
   | TradeCue | `shared-org` | shared identity with MMS |
   | IDFY | own org | no cross-identity requirement with MMS/TradeCue |
   | familytree/roots | own org | no cross-identity requirement with any other product |

   **Why not strict one-org-per-product everywhere** (the safer default):
   it would remove all the risk below (no role collisions, no
   admin-visibility leakage, no shared-policy constraint, zero judgment
   calls for future products) — but it also removes the one reason
   MMS/TradeCue are being merged in the first place: a real person
   shouldn't need two separate accounts/passwords/MFA enrollments for two
   products they both use. Strict 1:1 is the right *default*; it stops
   being right the moment there's a genuine identity-sharing need, which is
   why this is a per-pairing decision, not a single global policy in
   either direction.

   **Consequences of the MMS/TradeCue exception, to manage deliberately**:
   - *Role-namespace collision risk* — mitigate with a naming convention:
     prefix roles per product (`MMS_MEMBER`, `TRADECUE_ADMIN`) even inside
     the shared org, so `ClientApp.allowedRoles` can still express
     per-product role restriction despite the shared user pool.
   - *Shared `AccessPolicy`* — MMS and TradeCue are locked to identical
     MFA/geo rules for as long as they share the org. Revisit only if one
     product needs genuinely different security rules than the other
     (e.g., TradeCue handling money wanting stricter rules) — that would
     require extending `sso`'s schema to make `AccessPolicy` per-`ClientApp`
     rather than per-`Organization`.
   - *Shared admin visibility* — an admin with access to `shared-org` can
     see both MMS's and TradeCue's users; there is no code today to scope
     an admin to "MMS users only" within a shared org.
   - *Reversibility is asymmetric* — splitting `shared-org` later is a real
     migration whose cost scales with how much actual cross-usage
     happened, not with the code (see Phase 1). *Merging* two
     currently-isolated orgs later (if IDFY or familytree/roots ever needed
     shared identity) is comparably harder in the other direction — there
     is no way to auto-link two independently-created accounts as "the
     same person" without a deliberate account-linking flow. Neither
     direction is free; treat each grouping decision as a real product
     commitment, not a provisional default.
2. **User migration** — confirmed: one-time backfill of MMS `User` rows
   into `sso`'s `UserAccount`, with forced password reset and TOTP
   re-enrollment. No requirement to preserve credentials bit-for-bit.
3. **IDFY's vendored `mms-service` copy** — confirmed no in-process usage;
   safe to delete once IDFY is HTTP-only against MMS (already true for
   models/entitlements; just needs the embedded copy removed).
4. **Timeline** — confirmed: this is a planned migration, not a response to
   a live incident. No pressure to skip the readiness work in §9.
5. **Familytree/roots** — confirmed: included in the same wave, and
   isolated to its own `Organization` (§7 item 1) — no cross-identity
   requirement with any other product. Audited (§6) — same vendored-copy
   staleness as IDFY, plus a cleaner token-introspection pattern in
   `roots-service` worth using as the reference for Phase 4.
6. **`sso` production readiness** — confirmed requirement: `sso` must reach
   enterprise-grade, deploy-ready status *before* it becomes a hard
   dependency for any product's login. Full gap list in §9 — this is not
   satisfied yet.

---

## 8. Phase-by-phase plan

Each phase below has a goal, concrete work items, deliverables, exit
criteria (how you know it's actually done, not just "started"), and known
risks. This is still sequencing and scope, not code — no implementation
happens until each phase is separately reviewed and kicked off. Phases are
mostly sequential (each depends on the one before), except where noted.

### Phase 0 — `sso` production hardening (blocking gate)

**Goal**: `sso` reaches genuine production readiness before any product
takes a hard dependency on it for login. This is the gate — nothing in
Phase 1 onward should start against a non-hardened `sso`.

**Work items** (priority order, from the §9 gap list):
1. **Secret management — done, and live in production. The originally
   planned mechanism (GKE + External Secrets Operator) was abandoned in
   favor of Cloud Run; this item closes via a different, simpler path than
   first written here, updated 2026-08-17.**
   `gcloud` is authenticated to GCP project `idfy-platform` (the same
   project TradeCue's deploy pipeline uses), so GCP Secret Manager (+ Cloud
   KMS) was chosen over Vault/AWS for consistency with the rest of the
   platform — that part of the original plan held. What changed is the
   delivery mechanism:
   - **No Kubernetes cluster was ever provisioned, and none is coming** —
     see the cross-cutting `/Users/jomonvacha/Projects/CLOUD-DEPLOYMENT-PLAN.md`,
     which governs `sso`'s (and `mms`'s) real deployment across all five
     apps in this product family. `sso` deploys straight to **Google Cloud
     Run** via `.github/workflows/deploy-backend.yml`, with secrets
     injected at deploy time through `gcloud run deploy --set-secrets`,
     driven by a per-GitHub-Environment `SSO_SECRETS_MAPPING` variable
     (`shared`/`production` environments, one mapping each) that references
     Secret Manager secrets directly — `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`,
     `SPRING_MAIL_USERNAME`/`SPRING_MAIL_PASSWORD`, all versioned in Secret
     Manager, none committed anywhere.
   - **`k8s/base/` and `k8s/setup-gcp-secrets.sh` still exist in the repo
     but are stale, abandoned scaffolding** — written for the GKE path that
     was never built. Do not treat them as the real deployment story;
     nothing applies them. Worth a follow-up cleanup pass to delete them so
     a future reader doesn't mistake them for live infrastructure (this doc
     made exactly that mistake until this 2026-08-17 pass).
   - **`DATA_ENC_KEY` elimination in favor of Cloud KMS is live, not just
     coded.** `EncryptingStringConverter`'s `KmsBackend` is wired via the
     `DATA_ENC_KMS_KEY` env var, set per environment through
     `SSO_EXTRA_ENV_VARS` (e.g.
     `DATA_ENC_KMS_KEY=projects/idfy-platform/locations/us-central1/keyRings/sso/cryptoKeys/sso-data-enc-key-production`),
     with `roles/cloudkms.cryptoKeyEncrypterDecrypter` granted to the Cloud
     Run runtime service account. No Workload Identity/GKE machinery needed
     — Cloud Run's own runtime SA plus IAM is sufficient. Local dev is
     unaffected: still the ephemeral-key fallback.
   - **Peer-reviewed and committed** as `a8ec7b7` in the `sso` repo (10
     issues found and fixed pre-merge — see the original review notes
     preserved below for the record).
   - **Original pre-commit review notes (still accurate, preserved for
     context)**: 8-angle review (line-by-line, removed-behavior,
     cross-file, reuse, simplification, efficiency, altitude, conventions)
     found and fixed 10 real issues before this landed, the most severe
     being: `KmsBackend` could leak a gRPC client + shutdown-hook thread on
     every request if `DATA_ENC_KMS_KEY` were malformed; the secrets-setup
     script created the mail secrets without ever adding a version, which
     would have blocked secret materialization; and
     `DataEncryptionBackfillRunner` never checked the new
     `DATA_ENC_KMS_KEY` path, so it would have silently skipped
     legacy-plaintext migration forever. All fixed and verified, including
     a live end-to-end check against a real local Postgres (login +
     JWK-signing flows both exercised the modified encryption path
     successfully).
   - `docs/runbooks/secret-rotation.md` describes routine rotation for all
     secrets including the encryption key — still accurate, since KMS
     rotation doesn't depend on which compute platform calls it.
   **Closed.** No further work needed on this item — Secret Manager +
   Cloud KMS are both live in `shared` and `production`, confirmed via
   real deploys and hands-on testing directly against
   `https://sso-shared.exyon.com` this session (admin sign-in, MFA/TOTP
   flows, session and group management all exercised against the live
   Cloud Run instance, which stores its JWK private keys and MFA seeds
   through the KMS-backed `EncryptingStringConverter` path).
2. **WebAuthn + adaptive-auth test coverage — done.** 55 new tests added
   (`AdaptivePolicyServiceTest`, `WebAuthnServiceTest`,
   `WebAuthnControllerTest`), 115/115 passing, coverage gate met.
   `AdaptivePolicyService` (country allow/block lists, incl. precedence
   when a country is in both; new-device and geo-change step-up triggers;
   `getOrCreateDefault`'s create-vs-reuse behavior) went from 0% to 97%
   line coverage. The security-relevant part of WebAuthn is now directly
   tested: the inner `CredentialRepository`'s `lookup()`/`lookupAll()`
   filter out inactive/revoked credentials in-memory even when the backing
   query doesn't — that in-memory filter is what actually stops a revoked
   passkey from authenticating, and it's now proven (100% coverage on that
   inner class), not just present. Also covered: authorization checks (a
   user can't delete another user's credential), all error paths, and the
   controller's `X-Forwarded-For` IP-extraction and exception-to-HTTP-status
   mapping. **Deliberately not covered**: `finishRegistration`/
   `finishAuthentication`'s success path — those need a real signed WebAuthn
   attestation/assertion (a software-authenticator simulator, which isn't a
   current dependency, or hand-built CBOR/COSE fixtures), so `WebAuthnService`
   sits at 55% overall rather than higher. Flagged as a scoped follow-up
   rather than faked. Committed as `e2f622c` in the `sso` repo (push pending
   — hit the same 1Password SSH-agent flakiness noted earlier in this doc).
3. **Fix or remove the ZAP scan — done.** `zap-scan.yml` now builds the
   backend jar, boots it (dev profile, ephemeral key — throwaway CI-only
   instance) against a postgres service container, polls
   `/actuator/health` until ready, then scans the real live target instead
   of an empty `localhost:8080`. `continue-on-error: true` removed, so a
   real finding now fails the run instead of being silently swallowed. The
   `target_url` input still works for pointing at a real deployed
   environment later (e.g. staging, once item 4 below exists) instead of
   building a local instance for that run. Verified end-to-end locally
   before wiring into CI — not just YAML syntax, but the actual
   build→boot→health-check→real-HTTP-response sequence. Committed and
   pushed as `sso@bbf40ba`.
4. **Real deploy pipeline + staging environment — done, updated 2026-08-17.**
   The GKE/`k8s/base`-manifest plan in the original version of this item
   was superseded, not completed as written. What's actually live: a
   GitHub Actions CD pipeline (`deploy-backend.yml` + `deploy-frontend.yml`
   in the `sso` repo, `cd4bc8d` onward) that pushes to **Google Cloud Run**
   + Firebase Hosting on every push to `develop` (→ `shared` environment)
   and `release` (→ `production` environment). Two real, distinct
   environments exist today — `sso-shared.exyon.com` (backend
   `sso-api-shared`) and the production tier (backend `sso-api-production`)
   — each independently verified this session with real traffic: sign-in,
   MFA, session revocation, and the full admin console all exercised
   end-to-end against `sso-shared.exyon.com`, with fixes (DataInitializer
   startup crash, dropped roles claim on token refresh, group-endpoint
   500s, SMTP-based OTP delivery, a UI redesign) found via that live
   testing and then promoted through to `production` the same way a real
   release would be. `shared` functions as the staging tier this item
   originally asked for. **Closed** — a "staging environment" now exists
   in substance (a lower environment you can break safely before
   promoting), even though it's named `shared` rather than `staging` and
   runs on Cloud Run rather than GKE.
5. **Burn-in period — clock has started, not yet complete.** The `shared`
   environment has been carrying real interactive traffic (this session's
   own testing) since 2026-08-15; as of this update (2026-08-17) that's
   roughly 2 days, not the 2–4 weeks originally proposed. Unlike the
   original plan, this isn't synthetic smoke traffic on an idle
   cluster — it's genuine hands-on usage across auth, MFA, sessions, and
   admin flows, which arguably counts for more per day than synthetic
   traffic would, but it's still short of a real burn-in window and has
   surfaced real bugs during the window itself (see item 4) rather than
   after a clean settling period. **Recommendation**: don't reset the
   clock, but don't call this done either — let `shared` keep running
   under normal use for the remainder of a 2–4 week window from
   2026-08-15, watched via the existing Prometheus/Grafana stack, before
   treating Phase 0 exit criteria as met on this item.
6. **Load testing — still not done, unchanged since 2026-08-14.** The
   `loadtest/scripts` k6 scripts still haven't been run against anything.
   The difference now is there's a real, live target to point them at —
   `sso-shared.exyon.com` — instead of a hypothetical staging cluster.
   Nothing else about this item has changed; it's still open and is now
   the single most actionable remaining Phase 0 gap, since the blocker
   ("nothing to point the load test at") no longer exists.
7. **Frontend coverage — partially done, scope-limited by design.** Raised
   from 21.27% to 34.38% statements (228 → 309 passing tests), by covering
   every auth-critical file the original 50% target actually cared about:
   `ProtectedRoute` (100%, was 0% — the route guard itself), `lib/webauthn.js`
   (100%, was 18.8% — the actual ceremony-orchestration code, not just its
   already-tested base64 helpers), `MfaPage` (92.8%, was 38.8%),
   `settings/MfaCard` (90.7%, was 41.9%), `settings/PasskeysCard` (86.0%,
   was 44.2%), `ResetPasswordPage` (94.4%, was 0%), `SocialCallback` (100%,
   was 0% — including the security-relevant behavior that tokens get
   scrubbed from the URL via `history.replaceState` even if that call
   throws). **Did not reach 50% overall, on purpose**: roughly half the
   codebase's remaining uncovered statements are admin CRUD pages
   (`GroupsPage`, `UsersPage`, `ClientsPage`, `OnboardingPage`, etc. — ~880
   statements combined, all still at 0%), which were explicitly out of
   scope for "auth-critical flows, not coverage-for-its-own-sake." Closing
   the gap to 50% purely through auth-critical files isn't mathematically
   possible without diluting into that admin-CRUD surface; if a hard 50%
   number matters more than what's covered, that's a separate, explicit
   scope decision, not an oversight here.
8. **New capability: tenant-scoped self-service signup.** Discovered while
   drafting this plan (see §9 addendum below) — `sso`'s only public signup
   endpoint (`PublicController.signup`, `/api/public/signup`) creates a
   **brand-new `Organization` with its own first admin**. There is
   currently no endpoint for "add a regular end-user to an *existing*
   `Organization`'s user pool," which is what IDFY/TradeCue/familytree
   actually need (their whole current story is "end users sign up in the
   product," not "each signup creates a new tenant company"). This needs
   new work in `sso` — either a client-scoped public signup endpoint
   (looks up the calling `client_id`, resolves which `Organization` that
   client belongs to per §7 item 1's grouping — `shared-org` for MMS/
   TradeCue, its own org for IDFY/familytree — and assigns the right
   default `Role` within it), or a service-to-service admin API that each
   product's own signup UI calls server-side. The same endpoint design
   works for both shared and isolated groupings without per-product
   branching, since it's parameterized by `client_id`, not hardcoded to one
   org. Treat this as a required Phase 0 deliverable, not a Phase 3
   surprise. **Reconfirmed 2026-08-17, still missing**: `PublicController.java`
   still has exactly one endpoint, `POST /api/public/signup`, and it still
   only creates a brand-new `Organization`. No join-existing-org or
   invite-based endpoint exists anywhere in `sso`'s `web/` package.

**Deliverables — status as of 2026-08-17**: KMS/Secret Manager integration
merged and live ✅; WebAuthn + adaptive-auth test suites merged and
passing ✅; ZAP scan fixed ✅; a live lower environment (`shared`,
serving as staging) up and reachable ✅; frontend coverage report
showing the interim (auth-critical) target met ✅. **Still outstanding**:
burn-in period completed with no unresolved P1/P2 incidents (in progress,
~2 of ~14–28 days in); load-test baseline report (not started); new
tenant-scoped signup capability built and tested (not started).

**Exit criteria**: all eight items above closed, plus an explicit go/no-go
review before Phase 1 starts. Five of eight are closed as of this update;
the remaining three (burn-in, load testing, tenant-scoped signup) are the
actual path to Phase 1 — see the "path forward" note after §9.

**Risk**: this is the phase most likely to face pressure to skip or
shortcut, since nothing user-visible changes yet. It shouldn't be — this is
the one you explicitly asked to make enterprise-grade, and it's a one-time
cost paid before multiple products depend on it, not after.

---

### Phase 1 — Tenant + client setup (config only, in `sso`)

**Goal**: model the agreed tenant structure (§7 item 1: configurable
per-product grouping, isolate by default) in a hardened `sso` instance —
three `Organization`s, not one.

**Work items**:
1. Create three `Organization` records: `shared-org` (MMS + TradeCue),
   `idfy-org`, `familytree-org`.
2. Define the `Role` taxonomy per org — map MMS's existing `ERole`
   (ADMIN, MANAGER, MODERATOR, MEMBER) onto `sso` `Role`s for `shared-org`,
   using the `MMS_*`/`TRADECUE_*` naming convention from §7 item 1 to keep
   the two products' roles distinguishable despite the shared table;
   define independent taxonomies for `idfy-org` and `familytree-org` from
   scratch (no clone/template mechanism exists in `sso` today). Produce a
   written role-mapping table per org as part of this phase's
   deliverables — this is the thing Phase 2's user migration and Phase
   3/4's authorization checks both depend on.
3. Register four `ClientApp`s, each parented to its group's org:
   `mms-admin` + `tradecue` under `shared-org`; `idfy` under `idfy-org`;
   `familytree` under `familytree-org`. For each: redirect URIs, scopes,
   grant type (authorization_code + PKCE for the browser-facing apps;
   consider whether any need `client_credentials` for service-to-service
   calls).
4. Set `allowedRoles` per `ClientApp` — e.g. `mms-admin` only allows
   ADMIN/MANAGER/MODERATOR, not raw end-user accounts; within `shared-org`,
   `mms-admin` and `tradecue` each allow only their own `MMS_*`/
   `TRADECUE_*` roles despite sharing a user pool.
5. Configure `AccessPolicy` per org — `shared-org` gets one policy covering
   both MMS and TradeCue (document this as the known, deliberate tradeoff
   from §7 item 1, not a surprise later); `idfy-org` and `familytree-org`
   are each free to set independent MFA/geo rules from day one.
6. Document how each product's deployment receives its `client_id`/
   `client_secret` (via each product's existing CI secret store — never
   committed).

**Deliverables**: `sso` staging instance with 3 orgs + 4 clients
configured; written per-org role-mapping tables; documented `AccessPolicy`
settings per org and the rationale for `shared-org`'s single policy.

**Exit criteria**: verified via `sso`'s own admin UI (org switcher +
client registration, `AdminLayout.js`'s `activeOrg` picker) that all four
clients exist under the correct org with correct `allowedRoles`; role-
mapping tables reviewed and approved.

**Dependencies**: Phase 0 complete (don't configure real tenant structure
against a non-hardened instance).

---

### Phase 2 — User migration

**Goal**: one-time backfill of MMS's `User` rows into `sso`'s `UserAccount`,
per the confirmed decision (§7 item 2: forced password reset + TOTP
re-enrollment, no bit-for-bit credential preservation).

**Work items**:
1. Define the field mapping: `User.email`/`username`/`firstName`/`lastName`
   → `UserAccount` fields; MMS roles → `sso` `Role`s per Phase 1's mapping
   table.
2. Explicitly **do not** carry over password hashes or TOTP secrets —
   users get a forced password reset and TOTP re-enrollment on first
   `sso` login. Simpler and safer than trying to validate hash-algorithm
   compatibility between MMS's and `sso`'s password encoders.
3. Plan the identity-continuity mapping: MMS's `Member.userId` today points
   at MMS's `User.id`. Post-migration it needs to resolve via `sso`'s `sub`
   claim instead. Produce an explicit old-`User.id` → new-`sso`-`sub`
   mapping table as a migration artifact — this is what Phase 3 uses to
   re-key `Member` (and any other MMS table keyed by user id).
4. Define the user-notification plan: how existing users learn they need
   to reset password / re-enroll TOTP, and when (timed around the Phase 3
   cutover window, not before — no point prompting a reset for a system
   that isn't live yet).
5. **Dry run** against a copy of production data in a non-prod `sso`
   environment. Validate: row-count parity, no duplicate emails/usernames,
   the id-mapping table is complete (every MMS `User` has exactly one
   corresponding `sso` `UserAccount`).
6. Define rollback: this phase only *writes* to `sso`; it does not modify
   MMS's `User` table. If the dry run surfaces unacceptable issues, abort
   and re-run later — production MMS is untouched until Phase 3's cutover.

**Deliverables**: field-mapping spec; old-id → new-`sub` mapping table;
dry-run report (counts, spot-checks, discrepancies); user-notification
plan and copy.

**Exit criteria**: dry-run report reviewed and signed off; production
migration executed in a defined maintenance window; post-migration
verification query confirms row-count parity and a sample of spot-checks
pass.

**Risk**: highest blast-radius phase in this plan — it's identity data.
Should have an explicit human sign-off gate before the production run, not
just an automated check.

---

### Phase 3 — MMS becomes a resource server

**Goal**: MMS stops issuing/validating its own JWTs and trusts `sso`-issued
tokens instead; `Member`/entitlement data re-keys off the `sso` `sub`
claim.

**Work items**:
1. Configure MMS as an OAuth2 resource server (`jwk-set-uri`/`issuer-uri`
   pointed at `sso` — which, unlike the current TradeCue gap, actually
   serves these via Spring Authorization Server today).
2. Re-key `Member` (and any other MMS table keyed by user id — `UserPreferences`,
   `UserAvatar`, etc.) from MMS's old `User.id` to `sso`'s `sub`, using the
   mapping table produced in Phase 2.
3. Update MMS-UI's login screen to redirect to `sso`'s OAuth2 authorization
   endpoint (PKCE code flow) instead of posting to `/api/auth/signin`.
4. Decide and implement the **transition-window strategy** for already-
   logged-in users: either (a) accept both old MMS-issued tokens and new
   `sso`-issued tokens for a bounded window so nobody is forcibly logged
   out at the exact cutover instant, or (b) accept a hard cutover with
   forced re-login for everyone. Given §7 item 4 confirmed this is a
   planned migration with no live-incident pressure, (b) is simpler and
   defensible — but make the choice explicitly rather than by default.
5. MMS keeps a thin profile record (avatar, preferences — data `sso` has
   no concept of) keyed by `sub`, but stops being the source of truth for
   password/TOTP/session state.
6. Use the new tenant-scoped signup capability built in Phase 0 (item 8)
   for any MMS-side "create account" flow that still needs to exist post-
   cutover.

**Deliverables**: MMS resource-server config; `Member`/preferences re-key
migration; updated MMS-UI login flow; documented transition-window
decision.

**Exit criteria**: MMS-UI login works end-to-end via `sso` in staging; all
MMS API endpoints correctly authorize using `sso`-issued tokens; full
regression pass on previously-tested MMS flows (2FA and session management
now visibly live in `sso`, not MMS — see Phase 5 for when MMS's own
duplicate UI actually gets removed).

**Risk**: highest-touch phase for MMS's own codebase. The transition-window
decision (item 4) directly affects how disruptive cutover is to whoever is
logged into MMS-UI, IDFY-UI, TradeCue-UI, and roots-UI at that moment — all
of them, since they all currently validate MMS-issued tokens one way or
another.

---

### Phase 4 — IDFY, TradeCue, familytree cut over

**Goal**: each downstream product validates `sso`-issued tokens instead of
MMS-issued ones. Three products, three different current patterns, three
different cutover shapes — these are independent of each other and can be
sequenced in any order, including picking the lowest-risk one first as a
proof point before the others.

**IDFY** (currently: shared-secret local validation, no JWKS):
1. Replace `idfy-service`'s lightweight shared-secret `SecurityConfig`
   with a full OAuth2 resource server pointed at `sso`'s JWKS.
2. Remove the `JWT_SECRET` env var dependency between `idfy-service` and
   `mms-service` — nothing shares a secret anymore.
3. Delete the vendored `mms-service` directory from `idfy-platform`
   entirely (confirmed safe, no in-process usage — §7 item 3).
4. Update `idfy-ui`'s login flow to redirect to `sso`.
5. Regression-test `MmsModelClient`/`MmsEntitlementsClient` — these keep
   calling MMS over HTTP for models/entitlements, forwarding whatever
   bearer token they received; unaffected by the identity-issuer change
   as long as MMS (now itself an `sso` resource server per Phase 3)
   accepts that same token.

**TradeCue** (currently: already built for JWKS, just pointed at nothing):
1. Set `MMS_JWKS_URI`/`MMS_ISSUER_URI` (or rename to something
   issuer-neutral, e.g. `AUTH_JWKS_URI` — a naming cleanup worth deciding
   explicitly here) to `sso`'s real JWKS/discovery endpoints.
2. Verify `EntitlementClient.java`'s calls to MMS's
   `/api/users/me/entitlements` still work when forwarding an
   `sso`-issued bearer token.
3. Update `tradecue-ui`'s login redirect to `sso`.
4. This is the smallest cutover of the three — TradeCue's resource-server
   code doesn't change, only its configured issuer.

**Familytree/roots** (currently: token introspection against MMS):
1. Repoint `roots-service`'s `IntrospectionClient` from MMS's
   `/api/auth/introspect` to `sso`'s OAuth2 introspection endpoint (RFC
   7662 — confirm the exact path `sso` exposes as part of this work; Spring
   Authorization Server provides this by default but it hasn't been
   explicitly verified for this `sso` config yet).
2. Delete the vendored `mms-service` copy in `my-roots-project`, same as
   IDFY's.
3. Update `roots-ui`'s login flow to redirect to `sso`.
4. Separately, unrelated to this migration: archive or remove the
   duplicate `familytree-project` checkout (§2) — independent cleanup, can
   happen any time.

**Deliverables**: per product — updated resource-server/introspection
config, deleted vendored MMS copy (IDFY, familytree), updated frontend
login flow.

**Exit criteria** (per product, independently): end-to-end login plus at
least one entitlement-gated feature verified in staging before touching
production.

---

### Phase 5 — Decommission

**Goal**: remove MMS's now-dead identity code once nothing depends on it.

**Work items**:
1. Remove MMS's `AuthController` password/signup/signin endpoints (signup
   now happens via `sso`'s tenant-scoped signup capability from Phase 0
   item 8).
2. Remove TOTP 2FA code (`TwoFactorController`/`TwoFactorService`,
   `User.totpSecret`/`totpEnabled`/`totpRecoveryCodes` fields) — this is
   now `sso`'s job.
3. Remove `UserSession`/`SessionController`/`SessionService` — session
   management is now `sso`'s job.
4. Remove now-dead bootstrap logic (`DataInitializer.seedAdminUserIfConfigured`
   and similar) — admin provisioning moves to `sso`-side tenant setup
   (Phase 1).
5. Update `mms-ui`'s `AccountModal` (recently split into per-tab components
   — [ProfileTab.tsx](mms-ui/src/components/account/ProfileTab.tsx) etc.):
   remove or redirect the Security and Sessions tabs, since that state now
   lives in `sso`. Keep Profile/Preferences, which remain MMS's concern.
   Cross-reference: [MMS-Settings-Enhancements.md](MMS-Settings-Enhancements.md)
   documents this UI and will need another pass once this lands.
6. Remove `JWT_SECRET`/`ADMIN_PASSWORD`-related env vars from MMS and every
   downstream product's config, once stable.

**Deliverables**: MMS codebase with identity code removed; updated
`mms-ui` settings UI; updated `MMS-Settings-Enhancements.md`.

**Exit criteria**: a defined bake period post-cutover (proposed: 30 days)
with zero observed MMS-native auth usage in logs, then code removal; final
architecture review confirming the end state matches this document.

---

Each phase is its own reviewable change — nothing here should land as one
large PR. Phase 0 is the gate: no product should take a hard dependency on
`sso` for login until it closes.

---

## 9. `sso` production-readiness gap list (blocking Phase 0)

Audited directly against the code, not the README. Originally (through
2026-08-14): **solid security engineering and strong observability config,
undermined by thin test coverage, decorative security scanning, and zero
evidence of ever running outside a developer's laptop.** That last part is
no longer true, and the correction is substantial enough to change the
overall picture. **As of 2026-08-17: real security engineering, real
observability, real test coverage on the security-critical paths, and a
real deployed system carrying real traffic in two environments. What's
left is proving it under load and closing one missing product-integration
capability — this reads much closer to "genuinely production-hardening,
mid-rollout" than "side project" now.** Secret management (including
encryption-key rotation) is done and live (§8 Phase 0 item 1) — the
originally-planned GKE/cluster-application step was dropped, not
completed, because the deployment strategy changed to Cloud Run. `mvn
verify` runs clean (item 7). The most severe genuinely remaining gaps are
the missing load-test baseline (item 5) and missing tenant-scoped signup
(item 6) — both blockers for Phase 3/4, not Phase 0-internal quality
issues.

### What's genuinely solid already
- **Observability**: `monitoring/prometheus/alerts.yml` has real alerts
  (error rate, latency, auth-failure spikes, connection pool, JVM heap, pod
  restarts) and `recording-rules.yml` defines real SLO burn-rate math
  (99.9% availability target) — not boilerplate. Two working Grafana
  dashboards.
- **Backend test depth where it exists**: 15 backend test files covering
  MFA, lockout, rate-limiting, tenant isolation, and token lifecycle are
  genuinely security-focused, not filler.
- **Prod/dev config hygiene**: `docker-compose.yml` (dev) vs
  `docker-compose.prod.yml` are properly split, with `:?must be set` guards
  forcing real values for prod secrets rather than silently defaulting.
- **Runbooks are unusually detailed** (`docs/runbooks/*.md`, 500+ lines for
  disaster-recovery alone) with concrete RPO/RTO tables and runnable
  scripts — though never validated by an actual drill (see gaps).
- **Real hardening work already done**, per the project's own
  `FIX_PLAN.md`: IDOR fixes, PKCE, SecureRandom OTP generation, token
  rotation.

### Gaps that must close before Phase 0 is done
1. **No secret management for production — RESOLVED, corrected 2026-08-17.**
   Originally: `k8s/base/secret.yml` shipped literal
   `DATA_ENC_KEY: "REPLACE_ME"` and `DB_PASSWORD: "REPLACE_ME"`, and the
   planned fix (External Secrets Operator syncing GCP Secret Manager into a
   GKE cluster) hadn't been applied because no cluster existed. **What
   actually happened**: the GKE/ESO plan was abandoned when the deployment
   strategy moved to Cloud Run (see `CLOUD-DEPLOYMENT-PLAN.md`). Cloud Run
   reads secrets directly from GCP Secret Manager at deploy time via
   `gcloud run deploy --set-secrets` — no cluster, no operator, no synced
   `Secret` object needed. This is live today in both `shared` and
   `production`. `k8s/base/secret.yml`'s literal placeholders are gone from
   the real deployment path entirely (the file may still exist in the repo
   as dead scaffolding — see §8 item 1). **Rotation is fully solved**:
   `DATA_ENC_KEY` was eliminated in favor of Cloud KMS-backed encryption
   (`EncryptingStringConverter`'s `KmsBackend`), live via `DATA_ENC_KMS_KEY`
   in both environments' env vars, with the Cloud Run runtime SA granted
   `roles/cloudkms.cryptoKeyEncrypterDecrypter`.
2. **No deploy pipeline — RESOLVED, this was the doc's most significant
   error, corrected 2026-08-17.** This item previously stated `sso` "has
   never been deployed outside a dev machine." That's no longer accurate,
   and — checking dates — it's not clear it was ever verified rather than
   inferred from an absent k8s deploy job; the actual pipeline
   (`deploy-backend.yml`/`deploy-frontend.yml`, Cloud Run + Firebase
   Hosting) was added to the `sso` repo the same day this doc's §7 tenant
   model was last revised (`cd4bc8d`, 2026-08-15). **Current, directly
   observed reality**: `sso` runs live on Google Cloud Run in two
   environments — `shared` (`sso-shared.exyon.com`, service
   `sso-api-shared`) and `production` (service `sso-api-production`) —
   deployed via GitHub Actions on push to `develop`/`release`
   respectively, with Firebase Hosting serving the frontend and rewriting
   `/api/**` to the backend. This session used the `shared` environment
   directly and extensively: signed in as admin, walked the full admin
   console, found and fixed a startup-crashing bug
   (`DataInitializer`), a dropped-roles-on-refresh bug, group-endpoint
   500s, and non-functional SMTP delivery, then promoted every fix through
   to `production` the same way a real release would be. This is not
   "evidence of a workflow file" — it's confirmed via real HTTP responses
   (real `401`s from live Spring Security, not routing errors) and hands-on
   use over multiple sessions. The `k8s/base` manifests referenced by the
   original version of this gap were never the real path and should be
   treated as stale (see §8 item 1).
3. **Security scanning was decorative — fixed.** See §8 Phase 0 item 3.
4. **Test coverage had a critical blind spot — backend fixed, frontend
   partially fixed by design.** Backend: `WebAuthnService`/`WebAuthnController`/
   `AdaptivePolicyService` had zero tests; now covered (§8 Phase 0 item 2).
   Frontend: was 21% statements; now 34.38%, with every auth-critical file
   covered and admin CRUD pages deliberately left at 0% (§8 Phase 0 item 7).
   JaCoCo's line-coverage gate (35%) is comfortably cleared now that the
   suite runs at all (§8 Phase 0 item "Fix `mvn verify`"/gap 7). **Still
   open**: the E2E suite (3 spec files, no MFA/WebAuthn/admin-CRUD coverage,
   hardcoded `admin`/`AdminPass123!` credentials instead of a seeded
   fixture) — not touched by this pass, still a real gap.
5. **Load testing has never been run — still true as of 2026-08-17.**
   `loadtest/scripts` has 4 well-structured k6 scripts, but no results are
   committed anywhere — nobody has run them against a real target. What
   changed: a real target now exists (`sso-shared.exyon.com`, live on
   Cloud Run), so this gap no longer has an infrastructure blocker, only
   an "hasn't been done yet" status. No latency/throughput baseline exists
   for a system about to become a hard dependency for every product's
   login. This is now the most actionable open item in the whole gap list.
6. **No tenant-scoped self-service signup — still true as of 2026-08-17,
   reconfirmed directly against `PublicController.java`.**
   `PublicController.signup` (`/api/public/signup`) only creates a
   **brand-new `Organization` with its own first admin**; it remains the
   *only* endpoint in `sso`'s `web/` package for account creation. There is
   no endpoint for "add a regular end-user to an *existing* `Organization`'s
   user pool," which is what IDFY, TradeCue, and familytree/roots actually
   need for their current "users sign up in the product" flows. Every
   one-org-per-signup call today would fragment the tenant model decided in
   §7 item 1. This is missing functionality, not a config gap — it needs
   real backend work in `sso` before Phase 3/4 can replace any product's
   existing signup flow. Of the three genuinely open Phase 0 items, this is
   the only one that's pure development work rather than "wait and
   observe" (burn-in) or "run an existing script" (load testing) — it's
   the one to actually schedule engineering time against first if the goal
   is unblocking Phase 1 fastest, even though items 5 and 6 can run in
   parallel with it.
7. **`mvn verify` did not complete on this development machine's JDK — fixed.**
   `mvn test` failed with `Tests run: 59, Errors: 55` even on an *untouched*
   checkout (verified via `git stash` before/after the secret-management
   work — same failure count either way, confirming this predated and was
   unrelated to that work). Root cause, traced precisely: JaCoCo 0.8.13
   (the pinned coverage-agent version) couldn't instrument this JDK's class
   file format. The first class that tripped it was
   `com.sun.tools.attach.VirtualMachine` — loaded whenever Mockito's inline
   mock maker self-attaches its own instrumentation agent at runtime
   (Mockito 5+'s default behavior for every `mock()` call, not just
   final-class mocking). Once JaCoCo's agent failed to instrument that one
   class, the JVM's class-loading state was corrupted for the rest of that
   test fork, cascading into `ApplicationContext failure threshold
   exceeded` errors across every other `@SpringBootTest` sharing that fork.
   **Fix**: bumped `jacoco-maven-plugin` from `0.8.13` to `0.8.15` in
   `backend/pom.xml`. Verified via Maven Central's raw metadata (not the
   search index, which was stale and still listed `0.8.13` as latest) —
   `0.8.14` added official support for this JDK's classfile version,
   `0.8.15` (current) goes one version further. One-line change, no test
   code touched. Confirmed: `mvn verify` now passes clean — 60/60 tests,
   coverage gate met. Committed as `9e0ccf0` in the `sso` repo.

### Suggested Phase 0 priority order — updated 2026-08-17

Five of eight are done. What's left, in the order to actually work them:

1. ~~Secret management.~~ **Done** — see §8 Phase 0 item 1 above (Cloud
   KMS + Secret Manager, live via Cloud Run, not the originally-planned
   GKE path).
2. ~~Fix `mvn verify` on the CI/dev JDK (gap 7).~~ **Done** — see gap 7 above.
3. ~~WebAuthn + adaptive-auth test coverage.~~ **Done** — see §8 Phase 0
   item 2 above.
4. ~~Fix or remove the ZAP scan.~~ **Done** — see §8 Phase 0 item 3 above.
5. ~~Stand up a real deploy pipeline + a staging environment.~~ **Done** —
   see §8 Phase 0 item 4 above: live on Cloud Run in `shared` +
   `production`, verified with real traffic, not the originally-planned
   GKE/`k8s` path.
6. ~~Raise frontend coverage off 21%.~~ **Partially done** — see §8 Phase 0
   item 7: every auth-critical file covered (21.27% → 34.38% overall),
   admin CRUD pages deliberately left uncovered. Revisit only if a hard
   50% number is explicitly wanted regardless of what it covers.
7. **Run the load tests against the live `shared` environment**, record a
   baseline, feed it into the existing SLO recording rules. No longer
   blocked — just needs doing.
8. **Build tenant-scoped self-service signup** (gap 6 above) — real
   backend work, can run in parallel with items 7 and the burn-in clock,
   but must land before Phase 3/4 cutover regardless.
9. **Let the `shared` environment's burn-in clock finish** — started
   2026-08-15, targeting a 2–4 week window; don't gate Phase 1 on this
   alone, but don't skip it either.

Once 7 and 8 land and the burn-in window closes with no unresolved P1/P2
incidents, Phase 0's exit criteria are met and Phase 1 (tenant/client
config, §8) can start for real.

None of this is a rewrite — `sso`'s architecture is sound and its hardest
security engineering is already done (per `FIX_PLAN.md`). This is
operational maturity work: prove it in a real environment before three
products' logins depend on it.

## 10. Path forward — added 2026-08-17

Phase 0 is materially closer to done than this document previously
reflected — not because new work landed against the plan as originally
written, but because a parallel deployment effort
(`CLOUD-DEPLOYMENT-PLAN.md`) independently solved two of Phase 0's biggest
items (real secret management, a real deploy pipeline + staging-equivalent
environment) via Cloud Run instead of the GKE path this doc assumed. That
plan also deployed the standalone `mms` repo the same way, to
`mms.exyon.com`/`mms-shared.exyon.com` — worth knowing since Phase 3 will
eventually make MMS a resource server on this same infrastructure.

**Concrete next steps, in order:**

1. **Run the k6 load tests against `sso-shared.exyon.com` now.** This is
   pure execution, not design — the scripts exist, the target exists, the
   SLO recording rules to feed the results into exist. Nothing is blocking
   this; it's the highest-leverage single action available.
2. **Scope and build tenant-scoped self-service signup.** This is the one
   remaining item that's real engineering work, and it's on the critical
   path to Phase 3/4 (no product can cut over its signup flow without it).
   Start from §8 Phase 0 item 8's two design options (client-scoped public
   endpoint vs. service-to-service admin API) and pick one — that's a
   decision this doc deliberately left open, not an oversight.
3. **Let the `shared` burn-in clock run out** (targeting ~2026-08-29 to
   ~2026-09-12, i.e. 2–4 weeks from 2026-08-15) while 1 and 2 happen. This
   doesn't block engineering work, just the go/no-go call.
4. **Once 1–3 close, hold the explicit Phase 0 go/no-go review** this doc
   already calls for, then start Phase 1 (§8): create the three
   `Organization`s (`shared-org`, `idfy-org`, `familytree-org`), register
   the four `ClientApp`s, and configure `allowedRoles`/`AccessPolicy` per
   §7's already-resolved decisions. Nothing about Phase 1's design changed
   in this update — only Phase 0's status did.
5. **Minor cleanup, not blocking**: delete or clearly mark `sso/k8s/` as
   dead scaffolding so a future reader doesn't repeat this document's
   original mistake of treating it as the live deployment path.

**What did not change**: the Option C decision (§5), the tenant model
(§7 item 1), the phase sequencing (§8), and the fact that no product has
started depending on `sso` yet. This update is entirely about correcting
*how close Phase 0 is to done* — it does not reopen or revise any of the
architectural decisions already made.
