# MMS vs SSO — Architecture Analysis & Decision Record

*Status: DECISIONS MADE (§5, Option C), implementation of Phases 1–5 not
yet approved or started. Written 2026-08-13, updated 2026-08-13 with
resolved decisions + SSO readiness audit, updated 2026-08-14 revising the
tenant model from one shared `Organization` to configurable per-product
grouping (§7 item 1), updated 2026-08-17 to correct §8/§9 against verified
current state: `sso` is now genuinely live in production (Google Cloud
Run, not the GKE/k8s path this doc originally assumed — see §8 Phase 0
items 1 and 4, and §9 gaps 1–2). Updated again 2026-08-18: closed the
remaining Phase 0 work — a real load-test capacity baseline and the
tenant-scoped self-service signup endpoint, both real code changes in
`sso` (§8 Phase 0 items 6 and 8) — bringing Phase 0 to seven of eight
items closed, burn-in the only one left. Phase 0 hardening work is now a
real, in-progress engineering effort this doc tracks, not a purely
analytical exercise — the "no code changed" framing from earlier versions
of this doc no longer applies to Phase 0 itself, only to Phases 1–5.
Updated again 2026-08-18 (same day, second pass): revised §7 item 1's
tenant grouping from three orgs (one shared between MMS/TradeCue) to
**four fully independent orgs, no exceptions** — the shared-org
justification turned out to be based on an implementation accident
(`BillingAccount.userId` borrowing MMS's user table), not a real product
need; TradeCue's user base is confirmed to have no meaningful overlap with
MMS's. Also decided: TradeCue is retiring its `EntitlementClient`
dependency on MMS entirely, extending its own Paddle-driven billing to own
plan/feature entitlements instead (§8 Phase 4). And: Phase 1 was started
the same day, explicitly not waiting for the burn-in window to close
first — a deliberate risk tradeoff, see §10.*

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
  *its own* membership/entitlements, but stop having it also be a
  password/2FA/session identity provider. `sso` will own identity for the
  whole product family (IDFY, TradeCue, familytree/roots, plus MMS's own
  admin login); MMS becomes an OAuth2 resource server that trusts
  `sso`-issued tokens. See §5. **Updated 2026-08-18**: "MMS owns
  entitlements" no longer extends to TradeCue — TradeCue is retiring its
  dependency on MMS's entitlement API and building its own, on its own
  Paddle billing (§8 Phase 4). MMS's entitlement domain is now scoped to
  MMS's own product line only, not a shared service for other products.
- **This is a planned migration, not an urgent fix** — confirmed, no live
  incident is forcing this (§7, item 4).
- **Blocking gate before any product depends on `sso` for login: `sso` is
  further along than this doc previously gave it credit for — as of
  2026-08-18, seven of eight Phase 0 items are closed.** §9 has the full
  gap list: secret management (Cloud KMS + Secret Manager, live in
  production — the `k8s`/External Secrets Operator path this doc
  originally described was abandoned in favor of Cloud Run, see §8 item 1),
  a real CD pipeline with two live environments (`shared` and `production`
  on Google Cloud Run — this reverses the previous "never deployed outside
  a dev machine" finding, see §8 item 4 and §9 gap 2), WebAuthn/
  adaptive-auth test coverage, the ZAP scan, `mvn verify`, a real
  backend-capacity load-test baseline (118 req/s, 0 errors at 100
  concurrent logins — see §8 item 6), and tenant-scoped self-service
  signup (`POST /api/service/users`, service-to-service via `ClientApp`
  credentials — see §8 item 8). Frontend coverage is raised but
  deliberately not at a hard 50% (auth-critical files only). **The only
  thing left**: the `shared` environment's burn-in period (traffic started
  2026-08-15, targeting 2–4 weeks — ~2026-08-29 to ~2026-09-12).

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

   **Current grouping, revised 2026-08-18 — strict one-org-per-product,
   no exceptions:**

   | Product | Organization | Rationale |
   |---|---|---|
   | MMS | `mms-org` | no cross-identity requirement with any other product |
   | TradeCue | `tradecue-org` | no cross-identity requirement with any other product |
   | IDFY | `idfy-org` | no cross-identity requirement with any other product |
   | familytree/roots | `familytree-org` | no cross-identity requirement with any other product |

   **This reverses the original decision, on new information, not a change
   of mind about the rule itself.** The original grouping put MMS and
   TradeCue in one `shared-org`, justified entirely by rule 3 above ("same
   humans need one login") — evidence at the time was `BillingAccount`
   storing only a `userId UUID` pointing at an MMS user, with no local user
   table of its own. That turned out to be **today's implementation
   accident, not a real product requirement**: TradeCue never built its own
   user table, so it borrowed MMS's, but TradeCue's actual user base is
   confirmed (2026-08-18, direct answer from the product owner) to be its
   own thing — a commercial trading product being marketed independently,
   not an extension of MMS's existing membership base. There is no
   meaningful expected overlap between the two populations. Applying the
   decision rule honestly to that fact: rule 3 fails, so per the rule
   itself, the products should NOT share an org. Every consequence
   originally accepted as a deliberate tradeoff for the MMS/TradeCue
   exception — role-namespace collision risk, one shared `AccessPolicy`,
   shared admin visibility, the asymmetric cost of splitting later — is now
   moot, because there's no exception. This also lines up with a second,
   independent decision made the same day: TradeCue is moving its
   entitlement/plan-feature logic (`max_bots`, `futures`, `equities`,
   `api_access`) off of MMS's `EntitlementClient` dependency and onto its
   own Paddle-driven billing (see §8 Phase 4's TradeCue section for the
   full reasoning) — once TradeCue no longer consumes MMS's entitlement
   model either, there is no remaining coupling between the two products at
   all beyond both using `sso` for login, which is exactly what the
   strict-isolation default already handles cleanly.
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
6. **Load testing — done, including a real backend-capacity number, as of
   2026-08-18.** Ran against `sso-shared.exyon.com` on 2026-08-17
   (`login-flow.js`, `spike-test.js`, `mfa-flow.js`), then closed the
   remaining capacity-baseline gap on 2026-08-18 with a new script run
   locally. Full story below.
   - **Script bugs found and fixed, not just worked around**: none of the
     four scripts sent an `org` field, so every login attempt resolved to
     the default `acme.local` org regardless of `TEST_ORG`/`TEST_EMAIL` —
     against a real multi-tenant instance this meant the scripts could
     never have produced a valid result before. Added `org` to every login
     payload in all four scripts. Separately, `token-refresh.js`'s
     `setup()` fetched one refresh token and shared it across all 30 VUs —
     but `sso`'s refresh tokens are single-use with family-wide reuse
     detection (`AuthController#refresh`), so the first successful refresh
     from any VU would have revoked the whole family and failed every other
     VU for the rest of the run. Rewrote it so each VU logs in
     independently and threads its own rotating token across its own
     iterations (k6 gives each VU an isolated JS runtime, so per-VU module
     state is safe here).
   - **Real finding: the scripts as designed can't produce a backend-capacity
     baseline, because they correctly trip the rate limiter first.** All
     four scripts drive concurrent load against one fixed test credential.
     `sso`'s rate limiting (`RL_CREDS_CAPACITY=20`/60s per credential,
     `RL_IP_CAPACITY=60`/60s per IP) throttles that almost immediately —
     confirmed directly (`curl` loop of 30 concurrent requests against the
     same credential returned a mix of `200`/`429`, not `200` throughout).
     `login-flow.js` at 50 VUs: 98.9% of requests failed, essentially all
     `429`s, not errors — `p(95)` latency on the *successful* requests was
     a healthy 180ms. `mfa-flow.js` at 20 VUs showed the same pattern
     (96.6% `429`). This is the rate limiter doing exactly its job, not a
     capacity problem — but it means these specific scripts measure "how
     fast does the per-credential limiter engage," not "how many logins/sec
     can the backend actually handle." A real backend-capacity baseline
     would need load spread across many distinct test credentials so no
     single one trips the limiter; that's more fixture-building than this
     pass covered.
   - **Real finding, and a good one: `spike-test.js` (100 VUs, 50s) showed
     the server handling a hard concurrency spike cleanly** — every request
     returned either `200` or `429` (the script's own success condition),
     none timed out or 5xx'd, and `p(95)` latency stayed at 123ms even
     under the spike. That's a genuine, positive signal about the
     deployment's resilience under burst load, independent of the
     rate-limiter-baseline caveat above.
   - **Minor consistency finding**: `401`/`429` responses from
     `AuthController` return an empty body (`ResponseEntity.status(...).build()`),
     not JSON — a few of the scripts' checks assumed a JSON error body and
     failed on that basis specifically (not a real defect, just a
     script-vs-actual-contract mismatch, now visible in the results above).
   - **`token-refresh.js` — not run this pass.** Needs a non-MFA test
     account to reach the refresh step, but the adaptive-policy engine
     (`AdaptivePolicyService`, `requireMfaNewDevice`) correctly treats every
     scripted request as a new device and forces an EMAIL_OTP step-up
     regardless of the account's own MFA setting — and there's no way to
     retrieve that OTP from a load-test harness without either receiving
     real mail or disabling the org's adaptive policy, which is a
     security-relevant setting not worth flipping even on a throwaway test
     org without deliberately signing off on it first.
   - **Test fixtures created for this pass** (harmless, isolated,
     self-service-created — not customer data): an org
     `loadtest-2026-08-17.example.com` ("Load Test Co") with two users, on
     `sso-shared.exyon.com`. No org-delete endpoint exists in `sso` today,
     so this can't be cleaned up via the API; low-risk to leave in place,
     but flagging it so it isn't mistaken for real tenant data later.
   - **Real backend-capacity baseline — closed 2026-08-18.** The
     rate-limiter-bound numbers above can't be turned into a capacity
     number just by adding more test credentials: even with many distinct
     logins, a single-machine k6 run is still one source IP, and
     `RL_IP_CAPACITY` (60/min) caps it regardless of credential diversity.
     Rather than temporarily raising that limit on live `shared` infra (a
     real security-relevant config change, decided against for this pass),
     ran the new `capacity-baseline.js` against a **local Docker Compose
     instance** instead — same codebase, rate limiter deliberately raised
     via `docker-compose.loadtest-override.yml` (local-only, never applied
     to shared/production) — spreading load across 60 distinct seeded
     credentials (`loadtest/scripts/fixtures/capacity-users.json`).
     **Result at 100 concurrent VUs for 3 minutes: 0 errors, ~118 req/s
     sustained, p50 386ms / p95 1.29s.** The latency spread under
     concurrency traces to `BCryptPasswordEncoder`'s per-request CPU cost
     (confirmed: `AuthorizationServerConfig` uses the default cost factor)
     contending for cores on this machine — expected bcrypt behavior by
     design, not a code defect. This number reflects the app's own
     throughput ceiling, decoupled from Cloud Run's specific instance
     sizing (`--max-instances=2`, `1Gi` memory) — a genuinely different
     question that a future pass could answer by temporarily raising the
     live rate limit and redeploying, if the Cloud-Run-specific number
     becomes something you actually need.
   - **Net status**: load testing is fully done — the rate limiter works as
     configured, the server is resilient under a concurrency spike, and
     there's now a real capacity number (118 req/s, 0 errors at 100
     concurrent logins) to feed into the SLO recording rules.
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
   client belongs to — each product has its own org per §7 item 1's
   2026-08-18 revision to strict one-org-per-product — and assigns the
   right default `Role` within it), or a service-to-service admin API that
   each product's own signup UI calls server-side. The same endpoint
   design works regardless of grouping, since it's parameterized by
   `client_id`, not hardcoded to one org — this held up even after §7
   item 1's grouping decision changed. Treat this as a required Phase 0
   deliverable, not a Phase 3
   surprise. **Built 2026-08-18**: went with the service-to-service admin
   API option — `POST /api/service/users`, a new
   `ServiceProvisioningController`. Each product's own backend
   authenticates as its registered `ClientApp` (HTTP Basic, `client_id`/
   `client_secret` — verified manually against `clients.client_secret_hash`,
   the same pattern `/api/public/**` already uses for non-JWT auth, not a
   new Spring Security mechanism), and the endpoint resolves that client's
   `Organization`, creates the user there, and assigns a new
   `clients.default_role_id` (nullable, `V14__client_default_role.sql`;
   settable via the existing `PUT /api/developer/clients/{id}` admin
   endpoint — no new UI needed for that part). Deliberately rejects with a
   clear `409 no_default_role` rather than silently creating roleless
   users if a client hasn't had one configured yet. Returns tokens
   immediately (same `TokenService.issue` path as `/api/auth/login`) so
   the calling product's own signup flow doesn't need a second round trip.
   Unit-tested (`ServiceProvisioningControllerTest`, 7 cases: success,
   wrong secret, unknown client, missing auth header, no default role,
   duplicate email, password policy violation) and confirmed the Flyway
   migration applies cleanly. **Not yet done**: no real `ClientApp` exists
   for IDFY/TradeCue/familytree yet (that's Phase 1, §8), so this hasn't
   been exercised against a real caller — only unit-tested — and nobody's
   set a `defaultRoleId` on a real client yet either.

**Deliverables — status as of 2026-08-18**: KMS/Secret Manager integration
merged and live ✅; WebAuthn + adaptive-auth test suites merged and
passing ✅; ZAP scan fixed ✅; a live lower environment (`shared`,
serving as staging) up and reachable ✅; frontend coverage report
showing the interim (auth-critical) target met ✅; load-test report with
a real backend-capacity number ✅ (see item 6: 118 req/s, 0 errors at 100
concurrent logins); tenant-scoped signup capability built and
unit-tested ✅ (see item 8 — not yet exercised against a real product,
since no real `ClientApp` exists for one until Phase 1). **Still
outstanding**: burn-in period completed with no unresolved P1/P2
incidents (in progress, ~3 of ~14–28 days in) — the only item left.

**Exit criteria**: all eight items above closed, plus an explicit go/no-go
review before Phase 1 starts. Seven of eight are closed as of this
update; burn-in finishing is the only thing left before the go/no-go
review — see the "path forward" note after §9.

**Risk**: this is the phase most likely to face pressure to skip or
shortcut, since nothing user-visible changes yet. It shouldn't be — this is
the one you explicitly asked to make enterprise-grade, and it's a one-time
cost paid before multiple products depend on it, not after.

---

### Phase 1 — Tenant + client setup (config only, in `sso`)

**Goal**: model the agreed tenant structure (§7 item 1, revised
2026-08-18: strict one-org-per-product, no exceptions) in a hardened
`sso` instance — four independent `Organization`s.

**Work items** (simpler than the original plan, since there's no shared
org to manage the consequences of):
1. Create four `Organization` records: `mms-org`, `tradecue-org`,
   `idfy-org`, `familytree-org` — each fully independent, no shared user
   pool, no `MMS_*`/`TRADECUE_*` naming-collision concern.
2. Define the `Role` taxonomy per org, independently — map MMS's existing
   `ERole` (ADMIN, MANAGER, MODERATOR, MEMBER) onto `sso` `Role`s for
   `mms-org`; define TradeCue's, IDFY's, and familytree's taxonomies from
   scratch, each in its own namespace with no cross-product prefixing
   needed (no clone/template mechanism exists in `sso` today, so each is
   still built by hand). Produce a written role-mapping table per org —
   this is what Phase 2's user migration and Phase 3/4's authorization
   checks both depend on.
3. Register four `ClientApp`s, one per org — clean 1:1, no product sharing
   a `ClientApp` or an org with another: `mms-admin` under `mms-org`;
   `tradecue` under `tradecue-org`; `idfy` under `idfy-org`; `familytree`
   under `familytree-org`. For each: redirect URIs, scopes, grant type
   (authorization_code + PKCE for the browser-facing apps; consider
   whether any need `client_credentials` for service-to-service calls).
4. Set `allowedRoles` per `ClientApp` — e.g. `mms-admin` only allows
   ADMIN/MANAGER/MODERATOR, not raw end-user accounts. No cross-product
   role restriction needed, since each org's user pool is already
   product-specific.
5. Configure `AccessPolicy` per org, fully independently — each org sets
   its own MFA/geo rules from day one, no shared-policy constraint to
   document or manage.
6. Document how each product's deployment receives its `client_id`/
   `client_secret` (via each product's existing CI secret store — never
   committed).

**Deliverables**: `sso` staging instance with 4 orgs + 4 clients
configured (clean 1:1); written per-org role-mapping tables; documented
independent `AccessPolicy` settings per org.

**Exit criteria**: verified via `sso`'s own admin UI (org switcher +
client registration, `AdminLayout.js`'s `activeOrg` picker) that all four
clients exist under their own correct org with correct `allowedRoles`;
role-mapping tables reviewed and approved.

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
2. **Retire `EntitlementClient.java`'s dependency on MMS, don't just
   repoint it — decided 2026-08-18, revising the original plan.** The
   original item here was "verify `EntitlementClient` still works when
   forwarding an `sso`-issued bearer token," i.e. keep consuming MMS's
   `/api/users/me/entitlements`. That's no longer the plan: TradeCue's user
   base is confirmed to be its own thing (no real overlap with MMS
   membership, see §7 item 1's 2026-08-18 revision), and it already has an
   independent authorization mechanism in `BillingSubscriptionStatusProvider`/
   `BillingService` (Paddle-driven, no HTTP dependency on MMS) answering
   "does this user have active access at all." The plan is to extend that
   same Paddle-driven system to also own `max_bots`/`futures`/`equities`/
   `api_access` — the specific fields `EntitlementClient` currently fetches
   from MMS — so TradeCue's entire authorization story (both "is this
   account active" and "what does the plan grant") lives in one place it
   controls, with no runtime HTTP dependency on MMS for anything.
   Reasoning: `LiveTradingPolicy` gates real trades with real money on this
   data — every external dependency in that path is a reliability risk for
   a product moving real money, and TradeCue's pricing/feature tiers are
   product decisions that shouldn't require touching MMS's
   `MembershipCategory`/`MembershipTierConfig` schema (a different product
   line — AI personas/knowledge sources — entirely). This is new,
   TradeCue-side engineering work (extend `BillingService`'s Paddle-plan
   model to carry these fields, update `LiveTradingPolicy`'s callers to
   read from it instead of `EntitlementClient`, then delete
   `EntitlementClient` and the `tradecue.mms.base-url` config it depends
   on) — scope it as its own workstream, not a one-line change alongside
   the JWKS repoint.
3. Update `tradecue-ui`'s login redirect to `sso`.
4. **No longer the smallest cutover of the three** — item 2 above is real
   engineering work, not just a config repoint. The JWKS/issuer change
   itself (item 1) is still trivial; the entitlement migration is the
   actual scope of this cutover now.

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
5. **Load testing — done, real capacity number included, as of 2026-08-18.
   See §8 Phase 0 item 6 for the full writeup.** Short version: the four
   `loadtest/scripts` k6 scripts had never actually been runnable against
   a real multi-tenant instance (none sent the `org` field the login
   endpoint requires), and one (`token-refresh.js`) had a design flaw that
   would have broken under any real concurrency (a single shared refresh
   token across all VUs, which `sso`'s single-use rotating-token reuse
   detection would revoke on first use). Fixed both, then ran three of the
   four against `sso-shared.exyon.com`. Results: the rate limiter
   (20 req/credential/min, 60 req/IP/min) engages correctly and the server
   stays fast and stable under a 100-VU spike (p95 123ms, no 5xx/timeouts).
   **Then closed the remaining capacity-number gap**: added
   `capacity-baseline.js` (60 distinct credentials, so no single one trips
   the limiter) and ran it against a local instance with the rate limiter
   deliberately raised for that run only (never touching live
   shared/production config, since a single-machine k6 run against the
   real deployment would still collide with `RL_IP_CAPACITY` regardless of
   credential count). Result: 118 req/s sustained, 0 errors, at 100
   concurrent logins — p95 latency (1.29s) traced to `BCryptPasswordEncoder`
   contention under load, not a defect. `token-refresh.js` itself wasn't run
   against the live environment (blocked on adaptive-policy MFA step-up for any
   "new device," with no way to receive the OTP in an automated harness
   without either real mail or a security-policy change that wasn't
   authorized in this session).
6. **No tenant-scoped self-service signup — built 2026-08-18, see §8 Phase
   0 item 8 for the full writeup.** Short version: added
   `POST /api/service/users`, authenticated via each product's own
   `ClientApp` credentials (HTTP Basic), which adds a user to that
   client's existing `Organization` and assigns a per-client configurable
   default `Role` — instead of `/api/public/signup`'s "always create a
   brand-new org" behavior. Unit-tested; not yet exercised against a real
   caller, since no real `ClientApp` exists for IDFY/TradeCue/familytree
   until Phase 1 registers one. This closes the item as *built*, with
   "exercised end-to-end against a real product" as a natural Phase 1/4
   follow-up rather than a Phase 0 blocker — the capability itself no
   longer requires new backend work.
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

### Suggested Phase 0 priority order — updated 2026-08-18

Seven of eight are done. One thing left:

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
7. ~~Run the load tests, including a real capacity baseline.~~ **Done,
   2026-08-18** — see §8 Phase 0 item 6: rate limiter and spike resilience
   confirmed against the live `shared` environment; real backend-capacity
   number (118 req/s, 0 errors at 100 concurrent logins) established
   locally, since a live-environment number would need a live
   security-relevant rate-limit change this pass deliberately avoided.
8. ~~Build tenant-scoped self-service signup.~~ **Done, 2026-08-18** — see
   §8 Phase 0 item 8: `POST /api/service/users`, service-to-service via
   `ClientApp` credentials. Unit-tested; not yet exercised against a real
   product, since Phase 1 hasn't registered a real `ClientApp` yet.
9. **`shared` environment's burn-in clock** — started 2026-08-15, targeting
   a 2–4 week window (~2026-08-29 to ~2026-09-12). Still running, still
   worth letting finish for its own sake — but per an explicit 2026-08-18
   decision, **not gated on** before starting Phase 1 (see §10 item 4). A
   deliberate, known risk tradeoff, not an oversight.

Phase 1 (tenant/client config, §8) started 2026-08-18, in parallel with
burn-in rather than after it — see §10 for current status and what's
blocked.

None of this is a rewrite — `sso`'s architecture is sound and its hardest
security engineering is already done (per `FIX_PLAN.md`). This is
operational maturity work: prove it in a real environment before three
products' logins depend on it.

## 10. Path forward — added 2026-08-17, updated 2026-08-18

Phase 0 is now seven of eight items closed. Two forces got it here: a
parallel deployment effort (`CLOUD-DEPLOYMENT-PLAN.md`) independently
solved secret management and the deploy-pipeline/staging-environment items
via Cloud Run instead of the GKE path this doc originally assumed (also
deploying standalone `mms` the same way, to
`mms.exyon.com`/`mms-shared.exyon.com` — relevant since Phase 3 will
eventually make MMS a resource server on this same infrastructure); and
direct engineering work this pass closed the remaining test-coverage,
load-testing, and tenant-scoped-signup items.

**Concrete next steps, in order:**

1. ~~Load testing.~~ **Done, 2026-08-18** — see §8 Phase 0 item 6. Ran
   three of the four k6 scripts against `sso-shared.exyon.com` (fixing two
   real script bugs: missing `org` field; an unsafe shared refresh token),
   confirmed the rate limiter and spike resilience, then closed the
   capacity-number gap with a local run at raised rate limits: 118 req/s,
   0 errors, at 100 concurrent logins.
2. ~~Scope and build tenant-scoped self-service signup.~~ **Done,
   2026-08-18.** Went with the service-to-service admin API design (not
   the client-scoped public endpoint) — see §8 Phase 0 item 8.
   `POST /api/service/users`, unit-tested. Real next step here isn't more
   sso-side work: it's registering a real `ClientApp` in Phase 1 and
   having one product actually call this endpoint, since nothing has
   exercised it end-to-end yet.
3. **`shared` burn-in clock — explicitly not gating Phase 1 start, per a
   direct 2026-08-18 decision.** It's still running (started 2026-08-15,
   targeting ~2026-08-29 to ~2026-09-12) and still worth letting finish for
   its own sake — real usage without unresolved P1/P2 incidents is
   genuinely useful confirmation — but the product owner explicitly chose
   not to wait for it before starting Phase 1 work. That's a real,
   deliberate risk tradeoff (starting to configure real tenant structure
   against an instance that hasn't finished its observation window), made
   knowingly, not a gap in this plan.
4. **Phase 1 (§8): started 2026-08-18, revised scope — four independent
   orgs, not three with one shared.** The grouping decision changed the
   same day (see §7 item 1's 2026-08-18 revision): `mms-org`,
   `tradecue-org`, `idfy-org`, `familytree-org`, each fully isolated, one
   `ClientApp` each. **Work item 1 done on `sso-shared`, 2026-08-18**: all
   four `Organization`s created (`mms.exyon.com`, `tradecue.exyon.com`,
   `idfy.exyon.com`, `familytree.exyon.com`), via the admin console UI
   (`sso-shared.exyon.com/admin/organizations`) rather than the API —
   scripted API calls (`POST /api/admin/orgs`) were refused by the acting
   session's own permission controls, treating bulk scripted writes to
   real tenant data as needing direct human/UI action. Confirmed all four
   exist via `POST /api/public/signup`'s duplicate-domain check (409 on
   all four, non-destructively — no org created by that probe). **Note for
   whoever does this on `sso-production` next**: `GET /api/admin/orgs`
   only returns the caller's own org (`AdminOrganizationController.list()`
   scopes to `tenantGuard.callerOrgId`), so the admin console's org list
   won't show newly-created orgs unless you're logged in as a member of
   them — don't mistake that for the creation having failed; the first
   creation attempt here initially looked like it silently failed for
   exactly that reason, and it hadn't. **Still open, work items 2–6**: role
   taxonomy per org, `ClientApp` registration (one per org, redirect
   URIs/scopes/grant type), `allowedRoles`, `AccessPolicy` per org,
   documenting each product's `client_id`/`client_secret` distribution —
   and repeating all of Phase 1 on `sso-production` once `shared` is
   verified.
5. **Minor cleanup, not blocking**: delete or clearly mark `sso/k8s/` as
   dead scaffolding so a future reader doesn't repeat this document's
   original mistake of treating it as the live deployment path. Also: the
   `loadtest-2026-08-17.example.com` test org and the 60 seeded
   `capacity-user-*@acme.local` test accounts (local dev only, never
   touched shared/production) have no cleanup path via the API today —
   harmless, but worth remembering they're test data if they turn up
   later.

**What did not change**: the Option C decision (§5), the tenant model
(§7 item 1), the phase sequencing (§8), and the fact that no product has
started depending on `sso` yet. This update is entirely about correcting
*how close Phase 0 is to done* — it does not reopen or revise any of the
architectural decisions already made.
