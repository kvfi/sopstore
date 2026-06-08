# PHASE-STATUS

Honest delivery map: what was actually built in the "sprint through all 7
phases" session vs. what is stub / placeholder / not built.

**Headline:** This is a *scaffold*, not a product. The shape is right and
the marquee compliance-critical primitives (lifecycle state machine, hash
chain, HMAC webhook signer, tenancy plumbing) are implemented honestly. The
breadth — every entity, controller, migration, ADR — exists. The depth — full
test pyramid, ArchUnit suite, every adapter, every integration — is not
there. **Do not deploy this.**

---

> ### Update — 2026-05-30 (build-green session)
>
> The scaffold now **compiles, passes static analysis, and boots**. This was
> the doc's #1 recommended next step; cross-cutting gap #1 below is resolved.
> **No feature work was done** — the scaffold's depth is unchanged. Changes:
>
> - **Toolchain:** Gradle 8.11 → **9.5.1** (8.11 cannot *run* on JDK 25); added
>   `gradle.properties`. `libs.versions.toml` updated.
> - **Flyway now actually runs.** The Boot-4 `spring-boot-flyway` autoconfig
>   module was missing, so Flyway silently never executed. Added it plus a
>   `PerModuleFlywayMigrationStrategy` (each module keeps its own `V001` under a
>   `flyway_schema_history_<module>` table, baselined at 0). All 10 module
>   migrations apply on a clean DB.
> - **Schema/entity fixes for `ddl-auto: validate`:** 5 hash columns
>   `char(64)`→`varchar(64)`; added the Modulith `event_publication` table
>   (`common/V002`); `HibernateTenantResolver` now returns `UUID` to match
>   `@TenantId`.
> - **Boot blockers:** `TenantResolverFilter` registered via
>   `FilterRegistrationBean` (Modulith-observability CGLIB-proxies servlet
>   filters → NPE); `LifecycleStateMachine` de-finalized (CGLIB can't subclass
>   final).
> - **Phase-1 auth (partial) landed:** `StubAuthController` replaced by the real
>   `identity/auth/AuthController` + a `HomeController`; dev login seeded via
>   `scripts/dev-seed.sql` (`admin@example.com` / `admin`, all-zeros dev
>   tenant). Form login verified end-to-end (302 → `/procedures`).
> - **Dev ergonomics:** OTLP metrics export silenced for `bootRun`; colorized
>   JSON console logs for `./gradlew bootRun` (`-Pdevlog=trim|pretty|full|off`).
>
> **Verified:** `compileJava` + `compileTestJava`, Checkstyle, SpotBugs,
> ErrorProne + NullAway all pass; app boots, `/actuator/health` = UP, form login
> works. **Test suite run under Docker (2026-05-30): 14 / 14 pass** (12 / 14 on
> first run; fixed by the module-boundary cleanup and the RLS work below).
>
> - **`ModulithVerificationTest` — ✅ FIXED (2026-05-30).** Had 11 boundary
>   violations. Fixed by: a new exposed `procedure.ProcedureApi` (+ internal
>   `ProcedureApiImpl`) that `lifecycle`/`execution`/`reporting` use instead of
>   `procedure.internal.ProcedureRepository`; moving `AuthenticatedUser` from
>   `identity.internal.AppUserDetailsService` to the exposed `identity` package;
>   `@NamedInterface("events")` on `procedure.events`; `allowedDependencies`
>   corrected to `audit :: audit-port`, `training :: training-api`,
>   `procedure :: events`; and dropping reporting's unused
>   `ChangeRequestRepository`. Test green; all static gates still pass.
>   *Follow-up:* the API still passes the `Procedure` entity across the boundary
>   (spec wants DTOs) — noted for a later pass.
> - **`MultiTenantIsolationTest` — ✅ FIXED + made real (step 2, 2026-05-30).**
>   Was failing on a data-setup bug and didn't exercise RLS. Now runs against a
>   dedicated container **as a non-superuser role**, provisions two tenants
>   in-context (+ an owner user to satisfy FKs), and asserts tenant B sees
>   nothing of tenant A via the ORM **and via raw SQL** — the raw-SQL check pins
>   Postgres RLS specifically (the ORM `@TenantId` filter alone can't be the
>   thing under test). See the RLS mechanism below.

---

> ### Update — 2026-05-31 (feature session)
>
> First substantial **feature** session (prior ones were build-green + thin
> slices). Each item below was implemented and **verified end-to-end headlessly**
> against the running stack; the full test suite + all static gates stay green.
>
> - **Approval workflow engine (Phase 3)** — configurable multi-stage approvals
>   (sequential / parallel-within-stage / conditional), per-stage Part 11
>   e-signature with fresh password challenge, overdue escalation, change-control
>   + approval-queue UI. ADR-0006. *Was the single biggest gap.*
> - **Audit trail bug fix** — `AuditEvent.detail` (jsonb) was mapped as a plain
>   `String`, so **every audit insert had been failing** and `audit_event` held 0
>   rows. Added `@JdbcTypeCode(JSON)`; audit now persists (verified).
> - **Notifications (Phase 4)** — real in-app inbox (`/notifications`) + email
>   adapter (JavaMailSender, degrades without SMTP); wired to the workflow.
> - **Compliance dashboard (Phase 6)** — live KPIs + drill-downs via new exposed
>   `LifecycleApi` / `ExecutionApi`; every KPI cross-checked against SQL.
> - **Version diff (Phase 3)** — version history + side-by-side diff (java-diff-utils).
> - **Run history & analytics (Phase 5)** + **step reorder (Phase 2)**.
> - **RLS enforced at runtime (Phase 1)** — app connects as non-superuser
>   `sopstore_app`; migrations as owner via split Flyway credentials. ADR-0007.
>   Foreign-tenant query sees 0 rows.
>
> **Net:** the regulated change-control loop (author → change request →
> multi-stage signed approval → publish → notify → run → dashboard) now works as a
> product, not a scaffold. Breadth of integrations/hardening is still thin.

---

Legend: **DONE** · **PARTIAL** · **STUB** · **NOT BUILT**

---

## Phase 0 — Foundations

| Item | Status | Notes |
|------|--------|-------|
| Gradle build (Kotlin DSL) + version catalog | **DONE** | `build.gradle.kts`, `gradle/libs.versions.toml`. Spring Boot 4.0, Java 25, virtual threads on. |
| Spring Modulith setup | **DONE** | `@Modulithic` on app class, `@ApplicationModule` on every module's `package-info.java`. |
| Modulith subprojects per Gradle module | **NOT BUILT** | ADR-0002 explains why: idiomatic Modulith uses packages, not subprojects. |
| Docker compose (Postgres + Redis + MinIO + optional Keycloak) | **DONE** | `deploy/docker-compose.yml`. Image digests for redis/minio are placeholders. |
| Flyway baseline (per-module locations) | **DONE** | `application.yml` lists each module's location; baselines exist for every module. |
| Security baseline (Argon2id, CSRF, CSP, security headers) | **DONE** | `platform/security/SecurityConfig.java`. |
| OTel starter + structured logging | **PARTIAL** | `spring-boot-starter-opentelemetry` wired; `logback-spring.xml` JSON encoder configured. Not validated against a live collector. |
| `/actuator` locked down | **DONE** | Only `/health/**` + `/info` + `/prometheus` exposed; rest requires `ROLE_PLATFORM_OPS`. |
| CI scaffold (build, test, lint, SAST, container scan) | **PARTIAL** | `.github/workflows/ci.yml` has jobs for build / lint / CodeQL / OWASP / Trivy. Never run; will need iteration on first push. |
| Stub login page | **DONE** | `templates/login.html` served via `StubAuthController` (Phase 1 replaces). |
| Gradle wrapper jar committed | **NOT BUILT** | README instructs `gradle wrapper --gradle-version 8.11` to generate locally. |
| `Dockerfile` with digest-pinned JDK/JRE | **PARTIAL** | File present; digests are literal `PLACEHOLDER_*` — refresh before any build. |

---

## Phase 1 — Identity & tenancy

| Item | Status | Notes |
|------|--------|-------|
| `Tenant` + polymorphic `OrgUnit` hierarchy | **DONE** | Single `org_unit` table with `level` enum. ADR worth writing if questioned. |
| `User`, `Group`, `Role` (system enum), `MfaCredential` | **DONE** | Entities + repositories. |
| `RoleAssignment` table for ABAC overlays | **NOT BUILT** | RBAC only. ABAC noted in ADR-0001 follow-up. |
| Custom tenant-defined roles | **NOT BUILT** | System roles only. |
| Local auth (form login + Argon2id) | **DONE** | `AppUserDetailsService` + `SecurityConfig`. |
| OIDC SSO + login | **DONE (step 6, 2026-05-31)** | OIDC login against Keycloak (realm auto-imported from `deploy/keycloak/`), profile-gated via `application-sso.yml` + the `sso` profile so the default boot needs no IdP. **Verified end-to-end** (headless curl: authorize redirect → Keycloak login → code exchange → authenticated `/procedures` session → user provisioned). |
| SAML 2.0 SP config | **STUB (deferred, step 6)** | Dependency present; not wired. OIDC was done first; SAML's metadata/signed-assertion flow is impractical to verify headlessly here. Follow-up. |
| JIT provisioning from SSO claims | **DONE (step 6 + follow-up, 2026-05-31)** | `JitOidcUserService` provisions/updates an app `User` from OIDC claims on each login: the **tenant is resolved from the `tenant_slug` claim** via the new `tenancy.TenantLookup` (verified: SSO user provisioned under the `acme` tenant, not the dev default), and **claim roles are synced into `user_role`** (verified: `AUTHOR, QUALITY_MANAGER`) and the session authorities. **Caveat:** the slug→tenant registry read happens pre-auth (no tenant context); under prod RLS (non-owner role) it needs the bypass role. |
| SCIM 2.0 endpoint | **PARTIAL** | List/get implemented; create/patch return 501. Not RFC 7643/7644 conformant. |
| TOTP MFA | **DONE** | Real enrolment (QR code) + verification via `MfaService`. |
| WebAuthn / passkeys | **STUB** | `webauthn-server-core` on classpath; controllers/credential storage not built. |
| Tenant resolution filter | **DONE** | `TenantResolverFilter` reads JWT claim or session-attached `TenantAware` principal. |
| Hibernate `@TenantId` filter | **DONE** | `HibernateTenantResolver` + `@TenantId` on every tenant-scoped entity. |
| Postgres RLS policies | **DONE** | Every tenant-scoped table has `ENABLE ROW LEVEL SECURITY` + `current_tenant_id()` policy. |
| Bind `app.tenant_id` per connection | **DONE (step 2, 2026-05-30)** | `TenantAwareDataSource` (wraps the DataSource via a `BeanPostProcessor`) runs `set_config('app.tenant_id', <tenant-or-anonymous>, false)` on every connection borrow; the `security` Flyway migration `ALTER TABLE … FORCE ROW LEVEL SECURITY` so RLS applies to the owner role. Proven end-to-end by `MultiTenantIsolationTest` (raw SQL as a non-superuser role cannot see another tenant's row). **Now enforced at runtime too (2026-05-31):** the app connects as the non-superuser `sopstore_app` while migrations run as the owner — `PerModuleFlywayMigrationStrategy` uses `spring.flyway.*` (owner) creds when set, else falls back to the app datasource (tests unchanged). Role provisioned by `deploy/postgres-init/01-sopstore-app-role.sql`; compose wires `DATABASE_USER=sopstore_app` + `FLYWAY_USER=sopstore`. **Verified e2e (2026-05-31):** booted as `sopstore_app` (super=false, bypassrls=false), app read/write works, and with `app.tenant_id` set to a foreign tenant the role sees 0 of 18 rows. |
| `MultiTenantIsolationTest` | **PARTIAL** | One test asserting cross-tenant lookup is empty. Spec calls for an "exhaustive" suite covering every repository — not written. |
| Session rotation, idle/absolute timeout, concurrent limit | **DONE** (config) | Wired in `SecurityConfig` + `application.yml`. Not load-tested. |
| Break-glass admin + sealed-envelope recovery procedure | **NOT BUILT** | |
| Delegations ("Bob can approve while I'm out") | **NOT BUILT** | |

---

## Phase 2 — Authoring

| Item | Status | Notes |
|------|--------|-------|
| `Procedure` + `ProcedureVersion` + `Step` + `Attachment` | **DONE** | Entities + repos + migration with FTS gin index. |
| TipTap JSON body storage + editor | **DONE (step 3, 2026-05-30)** | `jsonb` body column; TipTap editor vendored via an offline esbuild bundle and wired into a procedure edit page that round-trips `body_json` — verified end-to-end (create → edit → save → reload). Rendering/interaction unverified (no browser); StarterKit only (tables/images/callouts/cross-refs not yet). |
| `Category`, `Tag`, template tables | **PARTIAL** | Migration present; `Template` entity not built. |
| Attachments uploaded to MinIO | **STUB** | `minio` client on classpath; upload controller not built. |
| Procedure list/detail/new/edit Thymeleaf views | **DONE (step 3)** | Offline frontend build added (node-gradle plugin + Tailwind + esbuild, wired into `processResources`); htmx/alpine/TipTap vendored into `static/` (fixed prior 404s — they were referenced but absent). Fixed `list`/`detail` accessor bugs (`${x}` → `${x()}` — `Procedure` has record-style accessors, not getters). |
| Procedure edit UI (rich-text body + step authoring + drag-reorder) | **DONE (reorder, 2026-05-31)** | Rich-text body editor + step-authoring UI + **step reorder** (up/down buttons that swap order index; `ProcedureService.moveStep`). **Verified e2e** (Alpha,Bravo,Charlie → reordered correctly). *Remaining:* true drag-and-drop (buttons today); per-step inline editing. |
| `ProcedureApiController` (REST) | **DONE** | list / get / create. No update/delete; no OpenAPI examples; no contract tests. |
| Postgres FTS | **DONE** (storage), **PARTIAL** (query) | `search_vec` generated column + gin index; `PostgresFtsSearchAdapter` queries it. Faceted filters, snippets, ranking weights — basic only. |
| Print view (cover, TOC, signatures block) | **PARTIAL — Word export (2026-05-31)** | `GET /api/v1/procedures/{id}/export.docx` renders a controlled-document Word file via Apache POI (`DocxExporter`): title + metadata cover line, the TipTap body walked into Word (headings, lists, bold/italic/code marks, quotes, rules), a steps table, and a "controlled copy / generated" footer. **Verified e2e** (valid .docx, body text present). Authored in the SPA via a TipTap editor on the procedure page. *Remaining:* PDF print view with TOC + signatures block. |
| Multilingual content / per-locale translations | **NOT BUILT** | |
| Conditional logic / branching steps | **NOT BUILT** | Field exists in `Step` schema; runtime not implemented. |
| Templates library (platform-shipped) | **NOT BUILT** | |

---

## Phase 3 — Lifecycle & change control

| Item | Status | Notes |
|------|--------|-------|
| Sealed-type `LifecycleState` + `LifecycleEvent` | **DONE** | Java 25 sealed interfaces + records. |
| Exhaustive `LifecycleStateMachine` | **DONE** | Compiler-checked switch on `(state, event)` pairs. |
| `LifecycleStateMachineTest` enumerating every illegal pair | **DONE** | `@ParameterizedTest` over `legalTransitions()` × pin-by-elimination over `(allStates × allEvents)`. Pins the spec acceptance criterion. |
| `ChangeRequest` entity + workflow shell | **DONE (engine, 2026-05-31)** | Configurable multi-stage approval engine in `lifecycle.internal.WorkflowService`: sequential stages, parallel (multi-role) within a stage, **conditional** stages (QA Director sign-off only for MAJOR/training-impact changes), per-stage Part 11 e-signature with a fresh password challenge, and overdue **escalation** (`WorkflowEscalationJob`, hourly). Stages stored as `workflow.stages_json` (tenant-editable; code default seeded on first use). UI: per-procedure change-control page + global approval queue (`/lifecycle/tasks`). **Verified e2e (2026-05-31):** MINOR → 2 stages → APPROVED with `REVIEWED,APPROVED` sigs; MAJOR+training → 3 stages incl. conditional; reject → back to DRAFT + CR REJECTED; wrong password → 403, no signature, no transition. **Per-tenant iteration (2026-06-05):** both `WorkflowEscalationJob` and `PeriodicReviewJob` now iterate active tenants (`TenantLookup.activeTenantIds()`) and run each tenant's work with `TenantContext` bound, so notifications no longer fail with "No tenant in context". Enumeration uses the `active_tenant_ids()` SECURITY DEFINER function (owned by the `sopstore_bypass_rls` break-glass role) so the runtime role is not granted BYPASSRLS. *Remaining:* DTOs at the module boundary. |
| Part 11 e-signature | **DONE (core, step 4, 2026-05-30)** | All three prior gaps closed: (1) re-auth now requires a **fresh password challenge** — `ReauthService.issue(userId, password)` verifies via a new identity `PasswordVerifier` (Argon2id), verified e2e (correct pw → token, wrong pw → 403); (2) tokens are **Redis-backed** (`RedisReauthTokenStore`, atomic GETDEL, TTL), verified against a live Redis; (3) the signature binds to the procedure's **canonical current version** (`ProcedureApi.currentVersionCanonical`), not `procedureId+op`. Single-use + user-binding pinned by `ReauthServiceTest`. **Remaining:** WebAuthn as an alternative fresh challenge (still stub); a slice test asserting the persisted signature hash equals the canonical-version hash. |
| Version diff (semantic, falling back to HTML) | **DONE (2026-05-31)** | Version history page (`/procedures/{id}/versions`) with a "new draft version" action (seeds from current body so prior versions stay immutable) and a side-by-side diff (`/procedures/{id}/diff?from=&to=`) built with `java-diff-utils` `DiffRowGenerator` over the pretty-printed body JSON (HTML-escaped, del/ins highlighted). **Verified e2e:** v0.1→v0.2 diff correctly shows the changed line as delete+insert. *Remaining:* semantic (text-extracted) diff of TipTap content vs raw JSON; metadata diff. |
| Periodic review batch job | **DONE** | `@Scheduled(cron = "0 0 2 * * *")` flips overdue EFFECTIVE → UNDER_REVIEW. |
| Effective / expiry / next-review dates | **DONE** (schema) | Surfaced in entity; UI not built. |
| Supersedes/superseded-by | **NOT BUILT** | |
| Training-impact flag on approval | **PARTIAL** | Flag exists on `ChangeRequest`; auto re-training is wired in Phase 4 listener regardless of flag. |

---

## Phase 4 — Training & competency

| Item | Status | Notes |
|------|--------|-------|
| `Curriculum`, `CurriculumItem`, `TrainingAssignment`, `TrainingCompletion`, `Quiz`, `QuizAttempt`, `Qualification` | **DONE** | Entities + tables. |
| Auto-assignment on new procedure version | **DONE** | `AutoAssignmentListener` on `ProcedureVersionCreated`. |
| Auto-assignment on hire / role change | **NOT BUILT** | |
| Read-and-acknowledge with e-signature | **NOT BUILT** | `TrainingCompletion` row has `version_sha256` field for this; UI + signature wiring missing. |
| Quizzes (MC / TF / short / banks / retake policy) | **PARTIAL** | Schema stores `questions_json`; no quiz runner UI or grading service. |
| Qualification grant + witnessed sign-off | **PARTIAL (2026-05-31)** | Training UI to grant/list qualifications per procedure (`TrainingController` + `identity.UserDirectory` for email↔id): a trainer qualifies an operator, which gates runs. Granting is now a **witnessed sign-off** — the trainer must re-enter their own password (`identity.PasswordVerifier` fresh-credential challenge) and hold a qualifying role (`@PreAuthorize` on `TrainingService.qualify`: TRAINER/QUALITY_MANAGER/TENANT_ADMIN/SUPER_ADMIN). Verified e2e: wrong password rejected, correct password grants, run start then passes. **Not built:** persisted signature/audit row for the grant, expiry/recert cadence, and the training matrix. |
| `TrainingApi.isUserQualifiedOn` consumed by execution module | **DONE** | Used as gate in `RunService.start`. |
| Training matrix view | **NOT BUILT** | |
| Notification fan-out on assignment / due-soon / overdue | **DONE (in-app + email, 2026-05-31)** | `NotificationService` now renders `templateKey` + model into title/body/link and dispatches: **IN_APP** persists a `notification_inbox` row (UI at `/notifications` with unread count + mark-read) and a `notification_delivery` record; **EMAIL** resolves the address and sends via `JavaMailSender` when SMTP is configured, degrading to a logged FAILED delivery otherwise (dev/air-gapped). Wired to the approval workflow (assignment on stage open, completion/rejection to requester) + overdue escalation. **Verified e2e:** inbox messages created on stage-open and completion; delivery `IN_APP:SENT`; mark-read decrements unread. *Remaining:* Slack/Teams/webhook adapters; tenant-branded `email_template` overrides (built-in templates today); digest mode + quiet hours. |

---

## Phase 5 — Execution

| Item | Status | Notes |
|------|--------|-------|
| `ProcedureRun`, `RunStepState`, `EvidenceItem`, `Deviation`, `RunSignature` | **PARTIAL** | All except `RunSignature` entity (table not added — needs to be created; covered by signature module logic from lifecycle). |
| `RunService.start` gated on `TrainingApi.isUserQualifiedOn` | **DONE** | Throws `SecurityException` if not qualified — pins the spec acceptance criterion. |
| Evidence kinds: text, measurement (+ unit), photo, file, signature, GPS, checkbox, selection | **DONE** (schema), **NOT BUILT** (UI) | |
| Tolerance check / auto-deviation on out-of-bounds measurement | **DONE (step 7, 2026-05-31)** | `RunService.recordMeasurement` raises an `OUT_OF_TOLERANCE` deviation when the value falls outside the step's `lowerBound`/`upperBound`. Verified e2e (9.9 vs max 5.0 → deviation raised; 4.5 → none). |
| Pause/resume across sessions | **DONE** (implicit, persisted state), **NOT BUILT** (UI) | |
| Run-mode UI (distraction-free, step-at-a-time) | **DONE (slice 1, step 7, 2026-05-31)** | `GET /runs/{id}` renders the run's steps step-by-step with evidence-capture forms, captured evidence, and deviations; `complete` ends the run. Added `StepRepository` + `ProcedureApi.currentVersionSteps/step`. Verified e2e (start → render → evidence → complete). Steps are created via the **step-authoring UI** and qualifications via the **training UI**, so the full **author → qualify → run → evidence → deviation** loop runs entirely through the UI with no SQL seeding (verified e2e 2026-05-31). |
| PWA: manifest + service worker (shell + procedure cache) | **PARTIAL** | `manifest.webmanifest` + `sw.js` present; service-worker registration in templates is missing; queued-evidence background sync **NOT BUILT**. |
| CAPA linkage on deviations | **STUB** | `linked_capa_id` column exists; no CAPA module. |
| Run history & analytics (avg time, bottleneck detection) | **DONE (basic, 2026-05-31)** | `/runs` lists recent runs (state, started, duration, deviation count) + per-procedure analytics (run count, completion %, avg duration, total deviations) via `RunService.history()`/`analytics()`. **Verified e2e** (renders live counts). *Remaining:* per-step bottleneck timing; the per-run deviation count is an N+1 over the top-200 window (fine for now, batch later). |

---

## Phase 6 — Audit, reporting, integrations

| Item | Status | Notes |
|------|--------|-------|
| `AuditEvent` append-only + hash chain | **DONE (bugfix 2026-05-31)** | `HashChainedAuditService` + `audit_event_no_update` trigger. **Critical fix:** `AuditEvent.detail` (jsonb) was mapped as a plain `String` without `@JdbcTypeCode(SqlTypes.JSON)`, so *every* audit insert failed (`varchar` → `jsonb`) and `audit_event` had **0 rows** — the audit trail had never persisted. Fixed; verified rows now written (`lifecycle.transition`, `change_request.opened`, `workflow.completed`, …). `verifyChain` previously passed only because there were no rows to check. |
| `verifyChain` end-to-end verifier | **DONE** | Re-hashes every row and walks the chain. |
| Tamper-evident PDF export | **NOT BUILT** | |
| Compliance dashboards (overdue reviews, training gaps, etc.) | **DONE (quality dashboard, 2026-05-31)** | `/dashboards/quality` shows live KPIs + drill-down tables: total/effective procedures, overdue periodic reviews, under-review, open change requests, pending approvals, open deviations. Aggregated across modules via new **exposed read APIs** — `lifecycle.LifecycleApi` (open CRs + pending approvals) and `execution.ExecutionApi` (deviation counts/feed) — plus `ProcedureApi`; no cross-module persistence access (Modulith verified). **Verified e2e:** every rendered KPI cross-checked equal to direct SQL counts. *Remaining:* training-gap KPI (needs a training read API), trend charts, scheduled/exported reports. |
| Custom report builder | **NOT BUILT** | Out of scope per spec. |
| Scheduled reports | **NOT BUILT** | |
| Webhook outbound — HMAC SHA-256 signing | **DONE** | `HmacSigner` + `WebhookDispatcher` with exact header shape per spec. |
| Webhook delivery retry + DLQ | **PARTIAL** | Schema includes `attempts`/`state`/`next_attempt`; retry scheduler not implemented. |
| Slack / Teams / Email adapters | **NOT BUILT** | |
| SharePoint / Confluence read-only mirror | **NOT BUILT** | |
| HR (Workday, BambooHR) / LMS / QMS connectors | **NOT BUILT** | |
| Inbound API auth (token + mTLS) | **STUB** | `oauth2-resource-server` on classpath; not configured. |
| `OFFLINE.md` air-gapped install guide | **DONE** | Documented end-to-end. **Not yet validated** against a real air-gapped cluster. |
| Helm chart skeleton | **PARTIAL** | `Chart.yaml`, `values.yaml`, `deployment.yaml`, `service.yaml`, `_helpers.tpl`. Missing: ingress, NetworkPolicy, PodDisruptionBudget, ServiceMonitor. |

---

## Phase 7 — Hardening

| Item | Status | Notes |
|------|--------|-------|
| `ModulithVerificationTest` (boundary checks) | **DONE** | Calls `modules.verify()`. Will likely fail on first run and require allowed-dependency adjustments. |
| ArchUnit boundary + entity-must-have-`@TenantId` test | **NOT BUILT** | Only Modulith's own verifier is wired. |
| Mutation testing (Pitest) on state-machine package | **NOT BUILT** | Plugin declared in libs.versions.toml; task not configured. |
| Contract tests (Spring Cloud Contract or Pact) | **NOT BUILT** | |
| Performance budget tests (Gatling) | **NOT BUILT** | |
| Accessibility audit (axe-core in CI) | **NOT BUILT** | |
| WCAG 2.1 AA compliance | **NOT BUILT** | UI is too thin to audit meaningfully. |
| i18n bundles | **PARTIAL** | `en`, `fr`, `de` seeded with a dozen keys each. Spanish, Portuguese, Japanese, Simplified Chinese not started. RTL handling not built. |
| Validation pack (IQ/OQ/PQ) | **DONE** (template) | `docs/validation/IQ-OQ-PQ-template.md`. |
| Pen-test readiness checklist | **NOT BUILT** | |

---

## Cross-cutting gaps worth calling out

These don't map to a single phase but are load-bearing for the spec:

1. ~~**No working build verified.**~~ ~~**Test suite hasn't run.**~~ **Resolved
   2026-05-30/31** — compiles, lints, boots, and the full **test suite passes
   under Testcontainers** (run repeatedly this session as the regression gate for
   the feature work). `ModulithVerificationTest` is green including the new
   exposed APIs and the lifecycle→notification dependency.
2. **Frontend assets are absent.** HTMX, Alpine, TipTap, Tailwind build, icons,
   PWA icons — none of these binary/vendored assets are committed. The
   templates reference paths that 404 today.
3. **No real OIDC/SAML test against any IdP.** The starters are on the
   classpath; the wiring isn't.
4. **No data-migration tooling from the legacy Python sopstore.** ADR-0001
   reserves ADR-0006 for this; it does not exist yet.
5. **Audit append-only trigger may not fire for the `sopstore` application
   role** depending on Postgres role configuration. The migration's idempotent
   `CREATE TRIGGER` assumes the trigger doesn't already exist; needs explicit
   role grants documented in `OFFLINE.md`.
6. **The Helm chart references secrets that the chart does not create.** Users
   must create `sopstore-db`, `sopstore-s3`, `sopstore-session` secrets before
   `helm install` succeeds; not documented yet.

---

## Recommended next steps (refreshed 2026-05-30, priority order)

0. ✅ **Get the build green.** Done — compiles, lints, boots (this session).
1. **Run the test suite under Docker.** `./gradlew test` (Testcontainers spins
   up Postgres/Redis/MinIO/Keycloak). Fix fallout — expect
   `ModulithVerificationTest` allowed-dependency tweaks first. This is the real
   "is the scaffold sound?" gate. Estimate: 0.5–1 day.
2. ✅ **Enforce RLS (done 2026-05-31).** `TenantAwareDataSource` binds
   `app.tenant_id` per connection + `security` migration FORCEs RLS, and the
   runtime now connects as the **non-superuser** `sopstore_app` while migrations
   run as the owner (`PerModuleFlywayMigrationStrategy` honours `spring.flyway.*`
   creds). Role bootstrap in `deploy/postgres-init/`; compose wired. Verified e2e
   (foreign tenant sees 0 rows; `bypassrls=false`). *Remaining:* mirror the role
   bootstrap + the two datasource credentials into the Helm chart/secrets.
3. ✅ **TipTap editor + vendored frontend assets (2026-05-30).** Offline build
   pipeline (Tailwind + esbuild via the node-gradle plugin, wired into
   `processResources`); htmx/alpine/TipTap vendored into `static/`; editor wired
   into a procedure edit page with a verified `body_json` round-trip. Also fixed
   (exposed by this work): `TenantResolverFilter` now resolves the tenant from
   the form-login **principal** (was only checking `auth.getDetails()`), and
   `ProcedureService.create` now persists `current_version_id`. **Follow-ups:**
   step drag-reorder; richer editor (tables/images/cross-refs); a vendored npm
   offline cache for true air-gap (OFFLINE.md); a browser/Playwright check of
   actual editor rendering.
4. ✅ **E-signature hardened (step 4, 2026-05-30).** Fresh password challenge
   (identity `PasswordVerifier`), Redis-backed single-use tokens
   (`RedisReauthTokenStore`), and the signature now binds to the **canonical
   current version** (`ProcedureApi.currentVersionCanonical`). Verified e2e +
   `ReauthServiceTest`. *Follow-ups:* WebAuthn challenge path; a slice test that
   the persisted signature hash equals the canonical-version hash.
5. **Write the exhaustive `MultiTenantIsolationTest` suite.** Pins the spec
   acceptance criterion. Estimate: 1 day.
6. ✅ **OIDC SSO end-to-end (step 6, 2026-05-31).** Keycloak realm import
   (`deploy/keycloak/`), profile-gated OIDC login (`application-sso.yml`), and
   `JitOidcUserService` provisioning — verified headless (authorize → Keycloak
   login → callback → session → provisioned user). **Follow-up done
   (2026-05-31):** multi-tenant tenant resolution from the `tenant_slug` claim
   (`tenancy.TenantLookup`) + claim roles synced to `user_role` — verified (SSO
   user under `acme` with `AUTHOR, QUALITY_MANAGER`). *Remaining:* **SAML**
   (deferred — impractical to verify headless); the pre-auth tenant-registry read
   needs the bypass role under prod RLS.
7. ✅ **Run-mode core — slice 1 (step 7, 2026-05-31).** Step-at-a-time run page
   + measurement/note evidence capture + automatic out-of-tolerance deviation +
   completion, gated on qualification. Verified e2e (ev=2, dev=1, run
   COMPLETED). *Follow-ups (the rest of Phase 5):* **step authoring UI** (steps
   are seeded via SQL today); other evidence kinds (photo/file/signature/GPS/
   checkbox/selection); pause/resume + run history & analytics; PWA offline
   execution + sync.
