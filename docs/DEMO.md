# DEMO — current state (2026-05-31)

This walks the **working end-to-end change-control loop**: author a procedure,
raise a change request, drive it through a multi-stage approval workflow with
electronic signatures, get notified, run the procedure, and watch the compliance
dashboard update. See [`PHASE-STATUS.md`](PHASE-STATUS.md) for the honest
real-vs-stub map of everything else.

## 0. Prerequisites
- JDK 25 (Temurin 25.0.3 verified), Docker, the Gradle wrapper (9.5.1).
- Backing services:
  ```
  docker compose -f deploy/docker-compose.yml up -d db redis minio
  ```
  The db container provisions the non-superuser `sopstore_app` runtime role on
  first init (`deploy/postgres-init/`). Migrations run as the owner `sopstore`;
  the app connects as `sopstore_app` so RLS is enforced (ADR-0007).

## 1. Seed a dev login
```
PGPASSWORD=sopstore psql -h localhost -U sopstore -d sopstore -f scripts/dev-seed.sql
```
→ `admin@example.com` / `admin`, under the all-zeros dev tenant. The dev admin
holds AUTHOR/REVIEWER/APPROVER/QUALITY_MANAGER so one login can drive every
approval stage. (Re-runnable.)

## 2. Run
```
./gradlew bootRun -Pdevlog=off
```
Wait for `Started SopStoreApplication`. Sign in at http://localhost:8080/login.

## 3. The change-control loop (all through the UI)
1. **Author** — `/procedures/new` → create. Open it, **Edit body** (TipTap),
   **Edit steps** (add steps; reorder with ↑/↓; set a measurement tolerance).
2. **Change request** — *Change control & approvals* → open a change request
   (title, reason, classification, training-impact). This submits the draft for
   review and starts its approval workflow:
   *Quality Review → Approval*, plus a *QA Director sign-off* for MAJOR or
   training-impacting changes.
3. **Approve** — *Approvals* (`/lifecycle/tasks`) shows your queue. Each approval
   is a Part 11 e-signature: re-enter your password to sign. Approve each stage;
   a wrong password is rejected and nothing is signed. The procedure reaches
   **APPROVED** when the last applicable stage completes.
4. **Notifications** — *Inbox* (`/notifications`) shows the assignment and
   completion messages (in-app; email sends too when SMTP is configured).
5. **Versions & diff** — *Versions & diff* → create a new draft version, edit it,
   and diff any two versions side-by-side.
6. **Run** — qualify an operator (*Qualifications*), then **Start a run**: step
   through it, capture measurements (out-of-tolerance auto-raises a deviation),
   and complete. `/runs` shows run history + per-procedure analytics.
7. **Dashboard** — `/dashboards/quality`: live KPIs (effective, overdue reviews,
   open change requests, pending approvals, open deviations) with drill-downs.

## 4. Health / API
- `/actuator/health` (UP), `/health/liveness`, `/health/readiness`;
  `/actuator/prometheus` requires `ROLE_PLATFORM_OPS`.
- REST: `GET/POST /api/v1/procedures` (list/get/create). SCIM: `GET /scim/v2/Users`.

## Verified this session (2026-05-31)
- Full test suite green under Testcontainers (Modulith boundaries, lifecycle
  state machine, multi-tenant isolation, reauth).
- Change-control loop, notifications, dashboard, version diff, run history, step
  reorder — all exercised end-to-end headlessly.
- **RLS enforced at runtime**: app runs as non-superuser `sopstore_app`; a foreign
  `app.tenant_id` sees 0 rows.

## Still stub / not built (see PHASE-STATUS)
SAML SSO, WebAuthn, SCIM writes, MinIO attachment upload, Slack/Teams adapters,
tamper-evident PDF export, PWA offline sync, integration connectors, and the
broader hardening pass (Pitest, Gatling, axe, full i18n).
