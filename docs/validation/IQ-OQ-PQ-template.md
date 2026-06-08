# Installation / Operational / Performance Qualification

This document is the template regulated customers can use to qualify the
installed sopstore platform for GxP use. It is intentionally generic; each
customer's QA function will localise it.

---

## 1. Installation Qualification (IQ)

| # | Test | Expected | Pass/Fail | Evidence |
|---|------|----------|-----------|----------|
| IQ-01 | Verify image digest matches published value | Digest in deployment = digest in release notes | | screenshot of `docker inspect` |
| IQ-02 | Verify TLS 1.3 enforced | `nmap --script ssl-enum-ciphers -p 443 <host>` reports TLSv1.3 only | | nmap output |
| IQ-03 | Verify CSP header present | `curl -I /login` returns `default-src 'self'; …` | | curl output |
| IQ-04 | Verify database extensions installed | `\dx` lists `pgcrypto`, `pg_trgm`, `pgvector` | | psql output |
| IQ-05 | Verify RLS enabled on every tenant-scoped table | `pg_class.relrowsecurity = true` for all listed tables | | SQL transcript |
| IQ-06 | Verify Flyway baseline applied | `SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1` | | psql output |
| IQ-07 | Verify air-gapped — no outbound network | `iptables`-blocked smoke test (see OFFLINE.md §3c) passes | | terminal log |

## 2. Operational Qualification (OQ)

| # | Test | Expected | Pass/Fail | Evidence |
|---|------|----------|-----------|----------|
| OQ-01 | Authentication: valid credentials | Login redirects to /procedures | | screenshot |
| OQ-02 | Authentication: invalid credentials | 401 + audit row written | | audit query |
| OQ-03 | MFA: TOTP enrolment + verification | QR scanned, 6-digit code accepted | | screenshot |
| OQ-04 | Lifecycle: every legal transition | LifecycleStateMachineTest passes in CI | | test report |
| OQ-05 | Lifecycle: every illegal transition rejected | LifecycleStateMachineTest passes in CI | | test report |
| OQ-06 | E-signature: re-auth required, meaning captured, payload SHA-256 bound | Approval flow records Signature row with correct hash | | DB row screenshot |
| OQ-07 | Audit: hash chain verifies for one procedure end-to-end | `verifyChain()` returns true after a full Draft → Effective → Retired sequence | | test report |
| OQ-08 | Multi-tenant: tenant B cannot read tenant A's data | MultiTenantIsolationTest passes | | test report |
| OQ-09 | Training: trainee with overdue assignment cannot start run | RunService throws SecurityException | | test report |
| OQ-10 | SCIM: list users via `/scim/v2/Users` | 200 + valid SCIM ListResponse | | curl output |

## 3. Performance Qualification (PQ)

| # | Test | Expected | Pass/Fail | Evidence |
|---|------|----------|-----------|----------|
| PQ-01 | Page load: procedure list with 1k procedures | p95 < 1 s | | Gatling report |
| PQ-02 | API: GET /api/v1/procedures/{id} | p95 < 300 ms @ 100 RPS | | Gatling report |
| PQ-03 | Audit insert throughput | ≥ 500 inserts/s sustained, no chain breaks | | Gatling + verifyChain |
| PQ-04 | Concurrent run sessions | 50 simultaneous runs, no evidence loss | | log analysis |
| PQ-05 | Backup + restore drill | DB hourly backups; full restore in < RTO | | timing log |

## Sign-off

| Role | Name | Signature | Date |
|------|------|-----------|------|
| Author |  |  |  |
| QA Reviewer |  |  |  |
| QA Approver |  |  |  |
