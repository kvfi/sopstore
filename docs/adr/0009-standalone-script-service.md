# ADR-0009 — Standalone versioned script service

**Status:** Accepted (Phase 1 scaffolded) · 2026-06-06

## Context

Procedures need to reference reusable, **versioned scripts** (e.g. the `RUN_SCRIPT`
step linking `deploy.sh @ v3`). We want a script repository that is independent of
the SOP lifecycle: editable on its own cadence, with full version history, and
reusable across many procedures.

Options considered: a new Spring Modulith **module** inside sopstore, vs a
**standalone service**. We chose the standalone service — its own database, API,
and deploy — so scripts evolve independently and could later serve other systems.

## Decision

A new Spring Boot application, **`script-service`** (`com.rightcrowd.scriptstore`),
living in this repo as an independently-deployable Gradle subproject (`:script-service`,
own `bootJar`/Docker/DB). Not under the `sopstore` modulith.

- **Model:** `Script` (tenant-scoped metadata + `current_version` pointer) and
  immutable `ScriptVersion` snapshots. **Auto-version on save:** every content save
  appends `current_version + 1` and advances the pointer; `restore(n)` re-publishes
  an old version's content as a new version. Own DB (`scriptstore`), own Flyway.
- **API:** `GET/POST /api/v1/scripts`, `GET/PUT /{id}`, `PUT /{id}/content`,
  `POST /{id}/restore/{n}`, `GET /{id}/versions`, `GET /{id}/versions/{n}`, `DELETE /{id}`.
- **Auth (Phase 1):** shared secret in `X-Service-Token` (`ServiceTokenFilter`);
  tenant in `X-Tenant-Id`; optional `X-Author`. Health endpoints open.

### Cross-service contract (sopstore ↔ script-service)

- sopstore calls the service with `X-Service-Token` + the procedure's tenant in
  `X-Tenant-Id`, and the acting user in `X-Author`.
- A `RUN_SCRIPT` step stores `{ scriptId, versionNo, name }` (name snapshotted for
  display/export even if the service is unreachable). The picker lists scripts and
  their versions; the author pins a specific `versionNo`.
- The PDF export and run-mode render `name @ v{versionNo}`; fetching live content is
  on-demand via `GET /{id}/versions/{n}`.

## Consequences

- Scripts version and deploy independently; sopstore depends on the service only at
  the HTTP boundary (graceful-degrade when unavailable: show the snapshotted name).
- **Deferred hardening / Phase 2:** sopstore HTTP client + `RUN_SCRIPT` linkage + the
  Scripts admin UI; dev `docker-compose` DB + `Dockerfile`; the service's own
  tenancy/RLS (currently tenant-scoped by query, not Postgres RLS); mTLS/JWT instead
  of a shared token; rate limiting and audit.
