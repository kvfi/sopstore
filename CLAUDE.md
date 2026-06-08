# CLAUDE.md — sopstore code map

Enterprise SOP (Standard Operating Procedure) management platform for regulated
industries (GxP / 21 CFR Part 11). **Status: scaffold, not a product** — the
shape is complete and the compliance-critical primitives are real, but breadth ≠
depth. Read `docs/PHASE-STATUS.md` for an honest real-vs-stubbed map of every
feature before assuming anything works. Do not deploy.

## Stack

- **Backend:** Java 25, Spring Boot 4.0, Spring Modulith 2.0, Hibernate 7,
  Flyway 11, Postgres 16 (pgcrypto / pg_trgm / pgvector), Redis 7, MinIO,
  OpenTelemetry. Gradle (Kotlin DSL), wrapper 9.5.1 committed.
- **Frontend:** React 18 + Blueprint 6 SPA in `web/` (Vite 6, React Router 7,
  TypeScript). Talks to a JSON-only API (`/api/v1/*`); there are **no
  server-rendered views**. Migrated from SvelteKit (old app dead at
  `web-svelte-legacy/` — ignore it).
- The backend once had Thymeleaf/HTMX pages (still referenced in README and some
  PHASE-STATUS entries); those have been superseded by the React SPA + JSON API.

## Layout

```
src/main/java/com/rightcrowd/sopstore/   Spring Modulith backend (see Modules)
src/main/resources/
  application*.yml                        base + per-profile config
  db/migration/<module>/                  per-module Flyway migrations (V001…)
  fonts/                                  IBM Plex TTFs embedded in the PDF export
  pdf/procedure.css                       theme CSS for the PDF export
  static/                                 legacy vendored assets (htmx/alpine/tiptap)
web/                                      React SPA (Vite + Blueprint + TanStack Query; see Frontend)
web-svelte-legacy/                        DEAD — do not touch
script-service/                           STANDALONE Spring Boot app (own DB/API) — see Script service
deploy/                                   docker-compose (incl. script-db:5433), Dockerfile, Helm, keycloak
docs/adr/                                 ADRs 0001–0009 (decisions live here)
docs/PHASE-STATUS.md                      authoritative feature status — read first
scripts/dev.sh                            one-command full stack
config/{checkstyle,spotbugs}/            strict lint config
```

## Run / build / test

```bash
scripts/dev.sh                 # infra (docker) + backend :8080 + SPA :5173 + dev seed; open :5173
scripts/dev.sh --backend-only  # skip the SPA
./gradlew bootRun -Pdevlog=off # backend alone (devlog: trim|pretty|full|off — colorized JSON console)
./gradlew test                 # THE test gate; Testcontainers spins Postgres/Redis/MinIO/Keycloak (needs Docker)
./gradlew checkstyleMain spotbugsMain   # static gates (also ErrorProne+NullAway on compile)
./gradlew build                # compile + test + lint + modulith verify
cd web && npm run dev|build|typecheck
```

- Dev login: **`admin@example.com` / `admin`** (all-zeros dev tenant), seeded by
  `scripts/dev-seed.sql`.
- ⚠️ README mentions `./gradlew integrationTest` and `modulithVerify` — those
  task names **do not exist** in `build.gradle.kts`. Modulith verification runs
  as `ModulithVerificationTest` under `./gradlew test`.
- Lint is strict and `-Werror`: 100-col lines, javadoc required, SpotBugs flags
  returning mutable collections (EI_EXPOSE_REP). Expect to defensively copy.

## Deployment profiles (`SPRING_PROFILES_ACTIVE`)

`saas` (multi-tenant, shared DB + RLS) · `single-tenant` (fixed tenant at boot) ·
`onprem` (air-gapped, OTel off) · `sso` (OIDC via Keycloak, opt-in) · `test`.
Config in `src/main/resources/application-<profile>.yml`.

## Backend modules (Spring Modulith)

Each module = a package under `com.rightcrowd.sopstore` with an
`@ApplicationModule` in its `package-info.java`. Convention:

- `<module>/` (root) — **public API**: exposed interfaces (ports), entities
  crossing boundaries, `@NamedInterface` packages.
- `<module>/api/` — **REST controllers** (`@RestController`).
- `<module>/internal/` — services, repositories, impls (NOT visible to other
  modules; Modulith enforces this).
- `<module>/events/` — published domain events.

| Module | Responsibility | REST base | Key public API |
|--------|----------------|-----------|----------------|
| `platform` | cross-cutting infra: security, web errors, persistence, config | — | (no deps; everything depends on it) |
| `tenancy` | multi-tenant isolation, RLS plumbing, tenant resolution | — | `TenantContext`, `TenantLookup`, `TenantResolverFilter` |
| `identity` | users, roles, password hashing, OIDC/form auth, SCIM, MFA | `/api/v1/me`, `/scim/v2/Users` | `UserDirectory`, `PasswordVerifier`, `AuthenticatedUser` |
| `procedure` | SOP authoring: versions(+label), rich steps(title/type/script), prerequisites, attachments, **PDF export** + themed templates | `/api/v1/procedures`, `/prerequisites`, `/prerequisite-types`, `/export-templates` | `ProcedureApi`; events `ProcedureCreated`, `ProcedureVersionCreated` |
| `lifecycle` | state machine, change requests, multi-stage approvals, Part 11 e-sig | `/api/v1/procedures/{id}/change-requests`, `/api/v1/approvals` | `LifecycleApi` |
| `training` | curricula, assignments, quizzes, qualifications | — | `TrainingApi.isUserQualifiedOn(...)` (gates runs) |
| `execution` | run mode, evidence capture, deviations, run analytics | `/api/v1/runs` | `ExecutionApi` |
| `audit` | hash-chained append-only audit trail + chain verifier | — | `AuditPort` (`@NamedInterface` audit-port) |
| `notification` | in-app inbox + email; templated, event-driven | `/api/v1/notifications` | `NotificationPort` (notification-port) |
| `search` | Postgres FTS over procedures | — | `SearchPort` (search-port) |
| `reporting` | compliance dashboard aggregating cross-module KPIs | `/api/v1/dashboard` | (read-only; consumes other APIs) |
| `integration` | outbound webhooks, HMAC-SHA256 signing | — | `HmacSigner`, `WebhookDispatcher` |
| `scripts` | BFF proxy to the standalone **script-service** (list/versions/CRUD); injects tenant+token | `/api/v1/scripts` | `ScriptServiceClient` (internal) |

Module dependencies are declared in each `package-info.java` `allowedDependencies`
and verified by `ModulithVerificationTest`. Cross-module access goes through the
exposed `*Api`/`*Port` interfaces only — never reach into another module's
`internal`. (Note: some APIs still pass entities across boundaries where the spec
wants DTOs — a known follow-up.)

## Key cross-cutting mechanisms (the load-bearing, gotcha-prone parts)

- **Multi-tenancy / RLS** (`tenancy` + `platform/persistence`):
  - `TenantResolverFilter` resolves the tenant per request from the authenticated
    principal (`AuthenticatedUser` / JWT claim) and binds `TenantContext`
    (ThreadLocal, virtual-thread safe).
  - Hibernate `@TenantId` filters every tenant-scoped entity;
    `HibernateTenantResolver` supplies the UUID.
  - Postgres **RLS** is the real enforcement: `TenantAwareDataSource` runs
    `set_config('app.tenant_id', …)` on each connection borrow; every
    tenant-scoped table has `ENABLE/FORCE ROW LEVEL SECURITY` +
    `current_tenant_id()` policy (the `security` migration, kept LAST).
  - **Runtime connects as non-superuser `sopstore_app`** (RLS enforced);
    migrations run as owner `sopstore` via split `spring.flyway.*` creds
    (`PerModuleFlywayMigrationStrategy`). See ADR-0007. Background jobs that span
    tenants use the `active_tenant_ids()` SECURITY DEFINER function (owned by the
    `sopstore_bypass_rls` role) rather than granting BYPASSRLS.
  - **Table names are prefixed**: steps = `procedure_step`, runs =
    `procedure_run`, etc. — not `step`/`run`.
- **CSRF** (`platform/security`): `CookieCsrfTokenRepository` (non-HttpOnly) +
  `SpaCsrfTokenRequestHandler` (dual-mode). The SPA reads `XSRF-TOKEN` cookie and
  sends it as `X-XSRF-TOKEN` header (plain). For the legacy HTML form path the
  submitted `_csrf` must be the **masked** token (XOR), not the raw cookie value.
  `CsrfCookieFilter` materializes the token on every request.
- **E-signatures (Part 11)** (`lifecycle`): approval decisions require a fresh
  password challenge (`ReauthService` → identity `PasswordVerifier`, Argon2id);
  tokens are Redis-backed, single-use (`RedisReauthTokenStore`); the signature
  binds to the procedure's **canonical current version**
  (`ProcedureApi.currentVersionCanonical`).
- **Lifecycle state machine** (`lifecycle.LifecycleStateMachine`): Java 25 sealed
  types + compiler-checked exhaustive switch on `(state, event)`. Was
  de-finalized for CGLIB. Pinned by `LifecycleStateMachineTest`.
- **Audit** (`audit`): `HashChainedAuditService`, `audit_event_no_update`
  trigger. `AuditEvent.detail` is jsonb — needs `@JdbcTypeCode(SqlTypes.JSON)`
  (a missing one silently dropped every insert historically).
- **Persistence/Flyway**: each module owns its migrations under
  `db/migration/<module>/` with its own `flyway_schema_history_<module>` table,
  baselined at 0. Migration order is the `spring.flyway.locations` list in
  `application.yml` — `security` MUST stay last.
- **Events**: Spring Modulith JPA event publication (`event_publication` table).
  `procedure` publishes `ProcedureCreated`/`ProcedureVersionCreated`; `training`,
  `search`, `notification`, `integration` consume them.
- **PDF export** (`procedure.internal.PdfExporter`): HTML/CSS→PDF via openhtmltopdf,
  IBM Plex fonts embedded from `resources/fonts`, themed by a `DocTemplate`
  (logo/accent/footer/font size). Replaced the old Word/POI exporter. TipTap body
  JSON is walked to XHTML. Unit-tested by `PdfExporterTest` (no DB).

## Script service (standalone, `script-service/`)

Separate independently-deployable Spring Boot app (`com.rightcrowd.scriptstore`,
own DB `scriptstore`, port **8090**) — a versioned script repository. **Auto-versions
on save** (`Script` + immutable `ScriptVersion`). Auth: shared `X-Service-Token`,
tenant via `X-Tenant-Id`, author via `X-Author`. sopstore's `scripts` module proxies
the SPA to it (token stays server-side). A `RUN_SCRIPT` step pins `script@version`.
ADR-0009. Run it: `docker compose … up -d script-db` then `./gradlew :script-service:bootRun`.

## Frontend (`web/`)

React 18 + Blueprint 6 + **TanStack Query** (`@tanstack/react-query`). TipTap rich-text
editor for step descriptions + the prerequisites field.

- **Data layer** = `src/lib/queries.ts` — every read is a `useQuery`, every write a
  `useMutation`, all wrapping `src/lib/api.ts`. Query keys in `qk`; mutations invalidate
  the keys they touch. `QueryClientProvider` + a global query-error toast live in `main.tsx`.
  (There is **no** `app-context.ts` anymore — `me`/badges are derived queries.)
- `src/lib/api.ts` — fetch transport (`api.get/post/put/del`, `upload`, `login`,
  `logout`). `XSRF-TOKEN` cookie → `X-XSRF-TOKEN` header on mutations; primes via
  `GET /api/v1/me`; 401 → `/signin`.
- `src/App.tsx` — routes: `/signin`, `/` (Dashboard), `/procedures`, `/procedures/:id`,
  `/approvals`, `/notifications`, `/runs`, `/scripts`, `/settings/configuration/{prerequisite-types,prerequisites,export-templates}`.
  Sidebar (`components/AppLayout.tsx`) has collapsible groups (state in localStorage).
- `src/components/` — `RichTextEditor` (TipTap) + `attachment-ref-node`/`prerequisite-ref-node`
  custom nodes; `Combobox` (Blueprint `Suggest`).
- `vite.config.ts` — dev server **:5173**, proxies `/api`,`/login`,`/logout`,`/actuator`
  to :8080 (same-origin session + CSRF).

## ADRs (`docs/adr/`)

0001 stack & architecture · 0002 single Gradle project (packages, not
subprojects) · 0003 tenancy strategy · 0004 hand-rolled state machine · 0005
search port · 0006 approval workflow engine · 0007 split DB credentials for RLS ·
0008 (svelte admin portal — now superseded by the React SPA).

## Conventions & gotchas

- Trust `docs/PHASE-STATUS.md` over the README for what actually works; the
  README has some aspirational/stale bits (Thymeleaf, `integrationTest` task).
- Honor module boundaries: add to `<module>/internal`, expose via the module's
  public interface, update `package-info.java` `allowedDependencies` if you add a
  cross-module dependency, and keep `ModulithVerificationTest` green.
- Static analysis is unforgiving — run `checkstyleMain spotbugsMain` before
  considering a change done; defensively copy collections returned from public
  methods.
- New tenant-scoped entity ⇒ `@TenantId` column + an RLS policy migration, or RLS
  isolation breaks.
- `./gradlew test` needs Docker (Testcontainers). Verify behavior headlessly
  against a running `scripts/dev.sh` stack when Testcontainers is impractical.
