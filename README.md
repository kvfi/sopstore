# sopstore

Enterprise SOP management platform for regulated industries.

**Status:** Multi-phase scaffold. See `docs/PHASE-STATUS.md` for an honest
real-vs-stubbed map of every spec area before assuming anything works.

## Stack

Java 25 · Spring Boot 4 · Spring Modulith · Hibernate 7 · Flyway · Postgres 16
(+ pgcrypto / pg_trgm / pgvector) · Redis 7 · MinIO · Thymeleaf + HTMX + Alpine
· OpenTelemetry. ADRs in `docs/adr/`.

## Quick start (local dev)

Prerequisites: JDK 25, Docker, Node/npm. The Gradle wrapper (9.5.1) is committed — use `./gradlew`.

**One command** brings up infra + backend (:8080) + the SvelteKit admin portal
(:5173), seeds the dev login, and streams both logs — then open
http://localhost:5173:

```bash
scripts/dev.sh        # Ctrl-C to stop; see scripts/README.md for flags
```

Or run the backend alone by hand:

```bash
# Bring up infra (Postgres, Redis, MinIO; optional Keycloak with --profile iam).
docker compose -f deploy/docker-compose.yml up -d db redis minio

# Run migrations + start the app.
./gradlew bootRun --args='--spring.profiles.active=saas'

# Open http://localhost:8080/ — stub login page (Phase 0).
```

## Deployment modes

Selected by `SPRING_PROFILES_ACTIVE`:

- `saas` — multi-tenant; shared DB with Hibernate filter + Postgres RLS.
- `single-tenant` — dedicated DB per customer; tenant ID fixed at boot.
- `onprem` — air-gapped; OTel exporters disabled; see `docs/OFFLINE.md`.

All three are supported from one image (see ADR-0001).

## Build & test

```bash
./gradlew build                       # compile, test, lint
./gradlew test                        # full suite incl. ModulithVerificationTest
                                       #   (boundary checks) — needs Docker (Testcontainers
                                       #   spins up Postgres/Redis/MinIO/Keycloak)
./gradlew checkstyleMain spotbugsMain # static analysis (ErrorProne+NullAway run on compile)
./gradlew bootJar                     # build the runtime jar
docker build -t sopstore:dev -f deploy/Dockerfile .
```

There is no separate `modulithVerify` or `integrationTest` task: module-boundary
verification is `ModulithVerificationTest` and the Testcontainers-backed tests
both run under `./gradlew test`.

## Module layout (Spring Modulith)

```
com.rightcrowd.sopstore
├── platform/        shared infra: security, config, web, observability
├── identity/        users, groups, roles, SCIM, SSO, MFA
├── tenancy/         org hierarchy, tenant resolution, RLS plumbing
├── procedure/       authoring (versions, steps, attachments, templates)
├── lifecycle/       state machine, change requests, signatures, workflows
├── training/        curricula, assignments, quizzes, qualifications
├── execution/       run mode, evidence, deviations
├── audit/           hash-chained AuditEvent + verifier
├── notification/    in-app, email, Teams, Slack, webhooks
├── search/          SearchPort + Postgres FTS adapter (OpenSearch later)
├── integration/     webhooks out, HR/LMS/QMS connectors
└── reporting/       dashboards, scheduled reports
```

## Docs

- `docs/adr/0001-…` through `0005-…` — architectural decisions.
- `docs/PHASE-STATUS.md` — honest delivery status per spec area.
- `docs/OFFLINE.md` — air-gapped install procedure.

## Migration from legacy Python sopstore

The prior FastAPI app is preserved at `../sopstore-legacy-python/`. Data
migration is a separate sub-project; design pending (will become ADR-0006 once
the Phase 2 data model is frozen).
