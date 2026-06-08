# ADR-0007: Split migration/runtime DB credentials to enforce RLS

- Status: **Accepted**
- Date: 2026-05-31
- Relates to: ADR-0003 (tenancy strategy), security/V001 (FORCE RLS)

## Context

Tenant isolation is defended by Postgres row-level security (ADR-0003):
per-table policies on `tenant_id = current_tenant_id()`, with `app.tenant_id`
bound per connection by `TenantAwareDataSource`, and `security/V001` issuing
`ALTER TABLE … FORCE ROW LEVEL SECURITY` so the table owner is not exempt.

But **superusers bypass RLS unconditionally**, and the application historically
connected as the schema-owning role `sopstore`. So in dev/prod the policies were
inert at runtime even though the mechanism was proven in tests. Fixing this is
not just "change the username": migrations need DDL and `FORCE ROW LEVEL
SECURITY` privileges that only the owner has, so migration and runtime cannot
share one credential.

## Decision

Run **migrations as the owner, runtime as a non-superuser**:

- A new role `sopstore_app` (`NOSUPERUSER NOBYPASSRLS`) is provisioned by
  `deploy/postgres-init/01-sopstore-app-role.sql`, with `ALTER DEFAULT
  PRIVILEGES FOR ROLE sopstore` so every table the owner migrates is
  automatically CRUD-grantable to it.
- `PerModuleFlywayMigrationStrategy` migrates with a dedicated owner DataSource
  built from `spring.flyway.{url,user,password}` when those are set, and falls
  back to the application DataSource otherwise — so the single-credential path
  (tests, Testcontainers, simple setups) is unchanged.
- `docker-compose` wires `DATABASE_USER=sopstore_app` for the app and
  `FLYWAY_USER=sopstore` for migrations.

## Rationale

- Opt-in via `spring.flyway.*` keeps the change zero-impact where a single
  credential is fine (the entire test suite still passes untouched).
- Default privileges avoid re-granting after every new migration.
- Keeps a single image/codebase serving all deployment modes (ADR-0001).

## Verification

- Booted with split credentials: app connects as `sopstore_app`
  (`rolsuper=false`, `rolbypassrls=false`), read/write paths work, and with
  `app.tenant_id` set to a foreign tenant the role sees **0** of 18 rows.
- Full test suite green with the change (empty `spring.flyway.url` → fallback).

## Consequences

- The Helm chart must mirror this: create the `sopstore_app` role/secret and set
  the two credentials. Tracked in PHASE-STATUS.
- The break-glass `sopstore_bypass_rls` role (security/V001) remains the only
  RLS-exempt path, for tenant onboarding and cross-tenant administration.
