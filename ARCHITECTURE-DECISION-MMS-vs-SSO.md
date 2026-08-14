# MMS vs SSO — Architecture Analysis & Decision Record

*Status: DECISIONS MADE, implementation not yet approved. No code has been
changed as part of this analysis. Written 2026-08-13, updated 2026-08-13
with resolved decisions + SSO readiness audit.*

## TL;DR

- **`mms` and `sso` are not the same system and don't compete for the same
  job.** `sso` is a generic, multi-tenant OAuth2/OIDC Authorization Server
  (identity only). `mms` is a single-tenant member-management backend that
  bundles identity *and* product-specific business logic (membership tiers,
  entitlements, AI model governance) behind one JWT.
- **`sso` is currently unused** — no code in `mms`, `IDFY`, or `TradeCue`
  references it. This is a green-field decision, not a migration of an
  existing plan.
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
- **Blocking gate before any product depends on `sso` for login: `sso`
  itself is not production-ready today.** §9 has the full gap list. Fixed
  so far: secret management (Cloud KMS + Secret Manager, replacing the
  literal `REPLACE_ME` k8s secrets) and `mvn verify` (was cascading to 55
  test errors on this JDK, now passes clean). Still open: thin test
  coverage (21% frontend, no WebAuthn/adaptive-auth tests), a ZAP security
  scan that scans nothing in CI, and zero evidence `sso` has ever run
  outside a dev machine. This has to close before Phase 1 of §8 starts.

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

1. **Tenant model — single `Organization`, `ClientApp`-level separation**
   (not one org per product). Reasoning, checked directly against `sso`'s
   schema:
   - `UserAccount` is hard-scoped 1:1 to `Organization`
     (`domain/UserAccount.java`) — separate orgs per product means fully
     separate, non-federated user pools. That defeats the point of SSO for
     the population that needs it most: MMS's own login already says
     *"Admin access only. End users sign up in the IDFY app"* — admin staff
     already manage governance across products from one place today, and
     per-product orgs would force them into 3+ separate logins.
   - `ClientApp.allowedRoles` (a `@ManyToMany` join to `Role`) is the
     built-in mechanism for population separation *without* separate orgs —
     register `idfy`, `tradecue`, `familytree`, and `mms-admin` as four
     `ClientApp`s under one org, and gate which roles can authenticate to
     which client. End users stay separated per product; staff can hold one
     identity across all of them.
   - The one caveat: `AccessPolicy` (MFA-on-new-device, geo allow/block) is
     also 1:1 per `Organization`, not per `ClientApp` — a single-org model
     means all products share one adaptive-auth policy today. Revisit
     separate orgs only if a product needs a genuinely different security
     policy (e.g., TradeCue handling money wanting stricter rules than
     familytree) — that would currently require extending `sso`'s schema to
     make `AccessPolicy` per-`ClientApp` rather than per-`Organization`.
2. **User migration** — confirmed: one-time backfill of MMS `User` rows
   into `sso`'s `UserAccount`, with forced password reset and TOTP
   re-enrollment. No requirement to preserve credentials bit-for-bit.
3. **IDFY's vendored `mms-service` copy** — confirmed no in-process usage;
   safe to delete once IDFY is HTTP-only against MMS (already true for
   models/entitlements; just needs the embedded copy removed).
4. **Timeline** — confirmed: this is a planned migration, not a response to
   a live incident. No pressure to skip the readiness work in §9.
5. **Familytree/roots** — confirmed: included in the same wave. Audited
   (§6) — same vendored-copy staleness as IDFY, plus a cleaner
   token-introspection pattern in `roots-service` worth using as the
   reference for Phase 4.
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
1. **Secret management — in progress, rotation problem fully solved.**
   Confirmed: `gcloud` is already authenticated to GCP project
   `idfy-platform` (the same project TradeCue's deploy pipeline uses), so
   GCP Secret Manager (+ Cloud KMS, see below) was chosen over Vault/AWS
   for consistency with the rest of the platform. Done so far, in the `sso`
   repo:
   - `k8s/base/secret.yml` (the static `REPLACE_ME` file) deleted, replaced
     with `k8s/base/secretstore.yml` + `k8s/base/external-secret.yml` — an
     External Secrets Operator `SecretStore`/`ExternalSecret` pair that
     syncs `DB_USERNAME`, `DB_PASSWORD`, `SPRING_MAIL_USERNAME`,
     `SPRING_MAIL_PASSWORD` from GCP Secret Manager into a k8s `Secret`
     with the shape the Deployment already consumes via
     `envFrom.secretRef`.
   - **`DATA_ENC_KEY` eliminated entirely, not just relocated.** Original
     plan was to move the raw AES key into Secret Manager, but investigating
     *why* it couldn't be safely rotated (see below) led to a better fix:
     `EncryptingStringConverter` now supports a Cloud KMS backend
     (`DATA_ENC_KMS_KEY`, a non-secret key *resource name* in
     `k8s/base/configmap.yml` — the key material itself never leaves KMS).
     The app calls KMS's `Encrypt`/`Decrypt` RPCs instead of holding a raw
     symmetric key; KMS handles key-version bookkeeping internally, so
     rotation is now either automatic (90-day period, matching the
     existing `KEY_ROTATION_DAYS` precedent for JWK signing keys) or one
     command (`gcloud kms keys versions create`) — no app-side migration,
     no downtime. Local dev is unaffected: it still uses the pre-existing
     ephemeral-key fallback, no live GCP dependency for `mvn spring-boot:run`.
     Full reasoning for choosing KMS over app-level key-versioning is in
     the conversation that produced this — the short version: less code to
     maintain, and it reuses the same Workload Identity auth already being
     set up for Secret Manager access.
   - `k8s/base/serviceaccount.yml` annotated for Workload Identity
     (`iam.gke.io/gcp-service-account: sso-backend@idfy-platform.iam.gserviceaccount.com`)
     — now used for both Secret Manager and Cloud KMS access.
   - `k8s/setup-gcp-secrets.sh` updated: creates the 4 remaining Secret
     Manager secrets, the KMS keyring + key (with automatic rotation
     configured), the GCP service account, and least-privilege IAM
     bindings scoped to exactly these resources (not project- or
     keyring-wide). **Not yet run** — needs a GKE cluster to exist first
     (confirmed: none does yet, this is greenfield) and a fresh
     `gcloud auth login` (the current session's token needs interactive
     re-auth). Cluster provisioning itself is out of scope for this item —
     it's a prerequisite, tracked separately.
   - `docs/runbooks/secret-rotation.md` rewritten — all secrets, including
     the encryption key, are now routine to rotate. Kept the explanation of
     *why* the old `DATA_ENC_KEY` approach was unsafe (one static key, no
     per-row version tracking, `DataEncryptionBackfillRunner` is a
     one-time legacy-plaintext migration guarded by a permanent completion
     marker — not a rotation tool, despite looking like one) so the same
     mistake doesn't get reintroduced for some future encrypted field.
   - Fixed a stale doc reference in `CLAUDE.md` (`DATA_ENCRYPTION_KEY` →
     the actually-used `DATA_ENC_KEY`) found while verifying the env var
     name.
   - New test coverage: `EncryptingStringConverterTest` gained a KMS-backend
     round-trip test. Deliberately **not** using Mockito for it — doing so
     surfaced a real, separate, pre-existing problem, see the new §9 item
     below.
   - **Peer-reviewed before commit** (8-angle review: line-by-line,
     removed-behavior, cross-file, reuse, simplification, efficiency,
     altitude, conventions). Found and fixed 10 real issues before this
     landed, the most severe being: `KmsBackend` could leak a gRPC
     client + shutdown-hook thread on every request if `DATA_ENC_KMS_KEY`
     were malformed (directly exploitable by the placeholder value that
     was, at the time, still sitting in `configmap.yml`); `setup-gcp-secrets.sh`
     created the mail secrets without ever adding a version, which — combined
     with External Secrets Operator's all-or-nothing sync — would have
     blocked the entire `sso-backend-secrets` object (including valid DB
     credentials) from ever materializing; and `DataEncryptionBackfillRunner`
     never checked the new `DATA_ENC_KMS_KEY` path, so it would have
     silently skipped legacy-plaintext migration forever in exactly the
     k8s topology this change introduces. All 10 fixed and verified —
     including a live end-to-end check against a real local Postgres
     (login + JWK-signing flows both exercised the modified encryption
     path successfully). Committed as `a8ec7b7` in the `sso` repo.
   **Still open**: kustomize build validated locally
   (`kubectl kustomize k8s/base` renders correctly), but nothing has been
   applied to a real cluster — there isn't one yet. Remaining before this
   item is fully closed: provision the GKE cluster, run
   `setup-gcp-secrets.sh`, install External Secrets Operator, fill in the
   cluster name/region placeholders in `secretstore.yml` and
   `configmap.yml`, and verify `SecretSynced=True`.
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
3. **Fix or remove the ZAP scan.** Either wire `zap-scan.yml` to actually
   start the app before scanning (ephemeral deploy or docker-compose step),
   or remove the job — a scan that scans nothing is worse than no scan.
   Remove `continue-on-error: true` once it's real, or gate merges on
   findings above an agreed severity.
4. **Real deploy pipeline + staging environment.** A CD job that applies
   `k8s/base` manifests to an actual staging cluster, secrets sourced from
   the KMS/Vault set up in item 1 — not `REPLACE_ME`.
5. **Staging burn-in.** Run `sso` in staging for a defined minimum period
   (proposed: 2–4 weeks) with synthetic smoke traffic, watched via the
   existing Prometheus/Grafana stack (already solid, per §9).
6. **Load testing.** Run the existing `loadtest/scripts` k6 scripts against
   staging; record a real latency/throughput baseline; feed it into the
   existing SLO recording rules (`monitoring/prometheus/recording-rules.yml`)
   so the 99.9%-availability target has a real number behind it.
7. **Frontend coverage.** Raise from the current 21% toward an interim
   target (proposed: 50%), focused on auth-critical flows first (login,
   MFA enrollment, session management), not coverage-for-its-own-sake.
8. **New capability: tenant-scoped self-service signup.** Discovered while
   drafting this plan (see §9 addendum below) — `sso`'s only public signup
   endpoint (`PublicController.signup`, `/api/public/signup`) creates a
   **brand-new `Organization` with its own first admin**. There is
   currently no endpoint for "add a regular end-user to an *existing*
   `Organization`'s user pool," which is what IDFY/TradeCue/familytree
   actually need (their whole current story is "end users sign up in the
   product," not "each signup creates a new tenant company"). This needs
   new work in `sso` — either a client-scoped public signup endpoint
   (looks up the calling `client_id`, assigns the right default `Role`
   under the existing shared `Organization`), or a service-to-service
   admin API that each product's own signup UI calls server-side. Treat
   this as a required Phase 0 deliverable, not a Phase 3 surprise.

**Deliverables**: KMS/Vault integration merged; WebAuthn + adaptive-auth
test suites merged and passing; ZAP scan either fixed or removed; staging
environment live and reachable; burn-in period completed with no
unresolved P1/P2 incidents; load-test baseline report; frontend coverage
report showing the interim target met; new tenant-scoped signup capability
built and tested.

**Exit criteria**: all eight items above closed, plus an explicit go/no-go
review before Phase 1 starts.

**Risk**: this is the phase most likely to face pressure to skip or
shortcut, since nothing user-visible changes yet. It shouldn't be — this is
the one you explicitly asked to make enterprise-grade, and it's a one-time
cost paid before multiple products depend on it, not after.

---

### Phase 1 — Tenant + client setup (config only, in `sso`)

**Goal**: model the agreed tenant structure (§7 item 1: one shared
`Organization`, `ClientApp`-level separation) in a hardened `sso` instance.

**Work items**:
1. Create the single `Organization` record.
2. Define the `Role` taxonomy for that org — map MMS's existing `ERole`
   (ADMIN, MANAGER, MODERATOR, MEMBER) onto `sso` `Role`s, plus decide if
   any product needs roles MMS doesn't currently have. Produce a written
   role-mapping table as part of this phase's deliverables — this is the
   thing Phase 2's user migration and Phase 3/4's authorization checks both
   depend on.
3. Register four `ClientApp`s: `mms-admin`, `idfy`, `tradecue`,
   `familytree`. For each: redirect URIs, scopes, grant type
   (authorization_code + PKCE for the browser-facing apps; consider
   whether any need `client_credentials` for service-to-service calls).
4. Set `allowedRoles` per `ClientApp` per the population-separation design
   in §7 item 1 — e.g. `mms-admin` only allows ADMIN/MANAGER/MODERATOR,
   not raw end-user accounts.
5. Configure the org's single `AccessPolicy` (MFA-on-new-device/geo, country
   allow/block) — one policy covers all four products under this tenant
   model; document that explicitly so it's a known, deliberate tradeoff
   (§7 item 1's caveat) and not a surprise later.
6. Document how each product's deployment receives its `client_id`/
   `client_secret` (via each product's existing CI secret store — never
   committed).

**Deliverables**: `sso` staging instance with org + 4 clients configured;
written role-mapping table; documented `AccessPolicy` settings and the
rationale for one shared policy.

**Exit criteria**: verified via `sso`'s own admin UI (`AdminOrganizationController`,
client registration) that all four clients exist with correct
`allowedRoles`; role-mapping table reviewed and approved.

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

Audited directly against the code, not the README. Bottom line: **solid
security engineering and strong observability config, undermined by thin
test coverage, decorative security scanning, and zero evidence of ever
running outside a developer's laptop. Not enterprise-grade yet — closer to
"well-architected side project with real security work started."** Secret
management (including encryption-key rotation) is now solved in code and
config, pending only cluster application (§8 Phase 0 item 1). `mvn verify`
now runs clean (item 7, fixed) — the most severe *remaining* gap is thin
WebAuthn/adaptive-auth test coverage (item 2 below), now that the suite
enforcing it actually completes.

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
1. **No secret management for production — in progress, see §8 Phase 0
   item 1.** `k8s/base/secret.yml` used to ship literal
   `DATA_ENC_KEY: "REPLACE_ME"` and `DB_PASSWORD: "REPLACE_ME"`; it's been
   replaced with GCP Secret Manager + External Secrets Operator config for
   DB/mail creds, not yet applied to a real cluster (none exists yet).
   **Rotation itself is now fully solved, not just deferred**: `DATA_ENC_KEY`
   was eliminated in favor of Cloud KMS-backed encryption
   (`EncryptingStringConverter`'s new `KmsBackend`) — KMS handles key
   versioning internally, so rotation no longer needs an app-side migration
   at all. See the rotation runbook and §8 Phase 0 item 1 for the full
   story.
2. **No deploy pipeline.** CI (`ci.yml`) builds, tests, and pushes images to
   `ghcr.io` — there is no job that applies the `k8s/base` manifests
   anywhere. Confirmed: `sso` has never been deployed outside a dev
   machine (no git history, workflow run, or manifest referencing a real
   cluster/domain).
3. **Security scanning is decorative.** `zap-scan.yml` targets
   `localhost:8080` on a schedule, but no step in that job ever starts the
   app — the scheduled scan runs against nothing. It's also
   `continue-on-error: true`, so findings never block anything even when it
   does find something. `FIX_PLAN.md` itself flags this as unresolved.
4. **Test coverage has a critical blind spot.** Frontend coverage is
   **21% statements** (real recorded number,
   `frontend/coverage/lcov-report/index.html`). Backend has **zero tests**
   for `WebAuthnService`/`WebAuthnController` and `AdaptivePolicyService` —
   i.e., no tests for two of the three features (WebAuthn/passkeys,
   adaptive auth) that are the biggest advantages over MMS's current auth.
   JaCoCo's line-coverage gate is set to 35% (barely above the current
   41.9% actual). E2E suite is 3 spec files with no MFA/WebAuthn/admin-CRUD
   coverage, and hardcodes `admin`/`AdminPass123!` instead of a seeded
   fixture.
5. **Load testing has never been run.** `loadtest/scripts` has 4
   well-structured k6 scripts, but no results are committed anywhere —
   nobody has run them against a real target. No latency/throughput
   baseline exists for a system about to become a hard dependency for
   every product's login.
6. **No tenant-scoped self-service signup.** Discovered while drafting the
   phase plan (§8, Phase 0 item 8) — `PublicController.signup`
   (`/api/public/signup`) only creates a **brand-new `Organization` with
   its own first admin**. There is no endpoint for "add a regular end-user
   to an *existing* `Organization`'s user pool," which is what IDFY,
   TradeCue, and familytree/roots actually need for their current "users
   sign up in the product" flows. Every one-org-per-signup call today would
   fragment the single-`Organization` tenant model decided in §7 item 1.
   This is missing functionality, not a config gap — it needs real backend
   work in `sso` before Phase 3/4 can replace any product's existing signup
   flow.
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

### Suggested Phase 0 priority order
1. Secret management — done except cluster application, see §8 Phase 0
   item 1 (now includes Cloud KMS-backed rotation, not just Secret Manager
   storage).
2. ~~Fix `mvn verify` on the CI/dev JDK (gap 7).~~ **Done** — see gap 7 above.
3. ~~WebAuthn + adaptive-auth test coverage.~~ **Done** — see §8 Phase 0
   item 2 above.
4. Fix or remove the ZAP scan — a security scan that scans nothing is worse
   than no scan, because it looks like coverage that doesn't exist.
5. Stand up a real deploy pipeline + a staging environment; run it there
   for a meaningful burn-in period before any product points at it.
6. Run the load tests against staging, record a baseline, feed it into the
   existing SLO recording rules.
7. Raise frontend coverage off 21% — pick a realistic interim target (e.g.
   50%) rather than jumping straight to a high bar.
8. Build tenant-scoped self-service signup (gap 6 above) — can happen in
   parallel with items 3–7 since it's independent new functionality, but
   must land before Phase 3/4 cutover regardless.

None of this is a rewrite — `sso`'s architecture is sound and its hardest
security engineering is already done (per `FIX_PLAN.md`). This is
operational maturity work: prove it in a real environment before three
products' logins depend on it.
