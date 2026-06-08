# ADR-0001: Stack, deployment topology, and modular monolith for sopstore platform

- Status: **Proposed**
- Date: 2026-05-28
- Supersedes: nothing (greenfield re-launch; prior FastAPI/Python sopstore archived to `../sopstore-legacy-python/`)

## Context

sopstore is being relaunched as an enterprise SOP management platform targeting
regulated industries (life sciences, manufacturing, financial services, healthcare).
The prior Python/FastAPI app does not meet the new requirements: 21 CFR Part 11
e-signatures, multi-tenant isolation with RLS, SAML/OIDC SSO, SCIM provisioning,
configurable approval workflows, immutable hash-chained audit log, training &
competency, run-mode evidence capture, deep integrations.

Directional decisions already made before this ADR (recorded here for traceability):

- The codebase replaces the existing directory; the Python app is archived for
  reference and as the source for a parallel **data-migration sub-project**.
- Brand name remains **sopstore** (Java package root `com.rightcrowd.sopstore`).
- **All three deployment modes** (SaaS multi-tenant / single-tenant cloud /
  on-prem air-gapped) are supported from day 1 via Spring profiles.

## Decision

### Runtime & framework

| Concern        | Choice                                                            |
|----------------|-------------------------------------------------------------------|
| Language       | **Java 25** (LTS, virtual threads, records, sealed types, pattern matching) |
| Framework      | **Spring Boot 4.0** (Spring 7, Jakarta EE 11)                     |
| Concurrency    | Virtual threads enabled by default                                |
| Nullness       | **JSpecify** annotations + NullAway (errors break the build)      |
| Modularity     | **Spring Modulith** with ArchUnit-enforced boundaries             |
| Build          | **Gradle Kotlin DSL**, convention plugins per module              |
| Web UI         | Thymeleaf + HTMX 2.x + Alpine.js + Tailwind (vendored, no CDN)    |
| Rich text      | **TipTap** (vendored), structured JSON storage                    |
| Public API     | REST under `/api/v1`, SB4 `@RequestMapping(version=...)`, OpenAPI 3.1 via springdoc |

### Persistence & storage

| Concern             | Choice                                                       |
|---------------------|--------------------------------------------------------------|
| RDBMS               | Postgres 16+ with `pgcrypto`, `pg_trgm`, `pgvector`          |
| ORM                 | Spring Data JPA + Hibernate 7                                |
| Migrations          | **Flyway** (versioned + repeatable), one location per module |
| Cache / session / queue | Redis 7+                                                 |
| Object store        | S3-compatible (MinIO on-prem, S3/Azure/GCS cloud)            |
| Search              | **Postgres FTS** for MVP; OpenSearch behind a port boundary for Phase 6 |

### Identity & security

| Concern        | Choice                                                            |
|----------------|-------------------------------------------------------------------|
| SSO            | SAML 2.0 + OIDC via Spring Security                               |
| Local auth     | Argon2id (break-glass only)                                       |
| MFA            | TOTP (RFC 6238) + WebAuthn (Yubico `java-webauthn-server`)        |
| Provisioning   | SCIM 2.0 endpoint under `/scim/v2/...`                            |
| Tenancy        | Hibernate `@TenantId` filter **+** Postgres RLS (defense in depth) |
| Tenant resolution | `OncePerRequestFilter` reads JWT/session, sets `app.tenant_id` GUC for RLS |
| E-signatures   | Re-auth on each signature; signature meaning captured; payload hash bound cryptographically |
| Audit          | Append-only `AuditEvent`, each row carries SHA-256 of prior row (hash chain) |
| Transport      | TLS 1.3 only; HSTS + strict CSP per spec                          |
| Secrets        | Env + Spring Cloud Config / HashiCorp Vault                       |
| Rate limiting  | Redis token bucket per principal per endpoint                     |

### Module boundaries (Spring Modulith)

`identity`, `tenancy`, `procedure`, `lifecycle`, `training`, `execution`,
`audit`, `notification`, `search`, `integration`, `reporting`, `platform`
(shared). Each module owns its tables (separate Flyway location), publishes
domain events via Modulith event publication, and exposes a Java API; cross-module
DB access is an ArchUnit violation.

### Observability

OpenTelemetry (OTLP) for metrics/traces/logs via `spring-boot-starter-opentelemetry`;
Prometheus scrape at `/actuator/prometheus`; structured JSON logs with trace
correlation; liveness/readiness probes.

### Deployment

- Multi-stage Dockerfile → slim **JRE-25** image; signed with cosign.
- Helm chart + Kustomize overlays; docker-compose for local dev (Postgres,
  Redis, MinIO, optional Keycloak).
- **One image, three modes** — behavior gated by Spring profiles (`saas`,
  `single-tenant`, `onprem`). Mode-specific logic kept behind a thin
  `DeploymentMode` boundary so swaps are mechanical.

## Deviations from the spec (proposed)

1. **State machine: hand-rolled over Spring Statemachine.** Lifecycle rules
   (Draft → InReview → Approved → Effective → UnderReview → Retired) expressed
   as sealed transitions + exhaustive switch, with JUnit5 parameterized tests
   enumerating every illegal transition and an ArchUnit test asserting the
   state-machine package has no external callers bypassing it. Rationale: rules
   are domain-specific and benefit from being expressible as records; Spring
   Statemachine adds runtime config complexity for marginal gain.

2. **Spring Authorization Server as optional issuer.** For customers who can't
   bring their own IdP and don't want Keycloak, ship `spring-authorization-server`
   as an optional `onprem-iam` profile. Adds ~3 days in Phase 1; defer until
   first customer asks.

3. **GraalVM native image deferred.** Build target stays configured but is not
   a Phase 0 deliverable; reassess at Phase 6 once the dependency surface is
   stable.

4. **OpenSearch deferred behind a port.** Phase 2 ships with Postgres FTS;
   `SearchPort` interface allows swapping to OpenSearch in Phase 6 without
   touching the `procedure` module.

## Risks & open questions

- **Spring Boot 4.0 / Spring 7 maturity.** GA ~6 months at time of writing.
  Most starters are stable, but niche libs (SCIM, WebAuthn) may need Jakarta EE 11
  forks. Mitigation: validate every starter explicitly in Phase 0; document any
  vendored patches.
- **"All three modes from day 1" cost.** Roughly **+30–40%** CI matrix work in
  Phase 0 (each compose flavour, each profile, isolation tests per mode).
  Accepted as project direction; mitigated by the `DeploymentMode` boundary.
- **Data migration scope.** Not yet sized. Will become **ADR-0002** once
  Phase 2 (Authoring) data model is stable and we can map legacy Python tables
  to new aggregates.
- **Air-gapped Maven offline repo** size, refresh cadence, and digest-refresh
  process — documented in `OFFLINE.md` by end of Phase 0.
- **Tenancy & RLS performance** under high cardinality (1000+ tenants, 100k+
  procedures) — load-test in Phase 7.
- **SCIM 2.0 conformance test suite** availability/licensing for air-gapped CI —
  verify in Phase 1.
- **WebAuthn library Jakarta EE 11 compatibility** — verify in Phase 1; fall
  back to vendored fork if needed.

## Consequences

**Positive:** spec-aligned stack; modular monolith preserves the option to
extract services later without rewriting boundaries; Hibernate filter + RLS gives
true defense in depth on tenancy; one image for three modes simplifies releases;
Java 25 + virtual threads removes a class of thread-pool tuning from the project.

**Negative:** Java 25 + Spring Boot 4 raises the onboarding bar; the multi-mode
requirement adds CI cost and constrains every dependency choice for the
lifetime of the project; air-gapped support forbids any vendor that requires
phone-home telemetry.

## Next

- Confirm this ADR (or land deviations and re-confirm).
- Execute **Phase 0 (Foundations)**: Gradle multi-module skeleton, Modulith setup
  with module stubs, docker-compose (Postgres + Redis + MinIO + optional
  Keycloak), Flyway baseline, security baseline (Spring Security form login
  + Argon2id + CSRF + CSP), `spring-boot-starter-opentelemetry`, structured
  logging, locked-down `/actuator`, CI scaffold (build, test, lint, SAST,
  container scan), and a stubbed login page that returns 200.
- Stop and check in.
