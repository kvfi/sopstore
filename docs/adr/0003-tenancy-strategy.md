# ADR-0003: Multi-tenant isolation strategy

- Status: **Accepted**
- Date: 2026-05-28
- Relates to: ADR-0001

## Context

The platform runs three deployment modes. SaaS multi-tenant is the hardest:
many tenants share one Postgres cluster and we must make cross-tenant data
access **structurally impossible**.

## Decision

Two enforcement layers, both mandatory:

1. **Hibernate `@TenantId` filter** on every tenant-scoped entity.
2. **Postgres Row-Level Security (RLS)** policy on every tenant-scoped table,
   bound to the session GUC `app.tenant_id`.

The current tenant is resolved by `TenantResolverFilter`
(`OncePerRequestFilter`) from the authenticated principal's JWT/session
claim. The filter:

- sets `TenantContext` (a `ThreadLocal` backed by Modulith scope) for the
  Hibernate filter;
- issues `SET LOCAL app.tenant_id = '<uuid>'` on the JDBC connection for RLS.

Anonymous endpoints (`/login`, `/actuator/health`) are exempt; they must not
touch tenant tables.

## Rationale

- **Two independent checks** catch each other's bugs. A missing `@TenantId`
  annotation is caught by RLS; a misconfigured RLS policy is caught by the
  Hibernate filter.
- RLS is enforced inside the database, so even raw SQL via `JdbcTemplate`,
  Flyway repair tooling, or operator queries cannot accidentally cross
  tenants without explicit `SET ROLE bypass_rls`.
- Single-tenant cloud mode keeps RLS active for free; the GUC is set once on
  connection acquisition.
- On-prem mode keeps the same code paths; no profile divergence.

## Tests

- `MultiTenantIsolationTest` (Phase 1 deliverable) exhaustively asserts that
  every Spring Data repository method, raw `JdbcTemplate` query, and JPA
  Criteria query returns nothing when the queried row belongs to another
  tenant.
- ArchUnit test forbids `@Entity` types without a `@TenantId` field unless
  they are explicitly marked `@PlatformShared`.

## Consequences

- Every JPA migration adds RLS policy alongside the table — a Flyway template
  helper enforces this.
- A break-glass `bypass_rls` role exists for admin tasks; its use is audited
  and rate-limited.
- Cross-tenant analytical queries (e.g. platform-level KPIs) live in the
  `reporting` module and run as `bypass_rls`; they're code-reviewed line by
  line.
