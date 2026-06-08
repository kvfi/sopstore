# sopstore admin portal (React + Blueprint)

A single-page admin portal for the sopstore quality/SOP platform. **React 18 +
Blueprint 6 + React Router 7**, built with Vite, talking to the Spring backend's
JSON API (`/api/v1/*`) — the backend domain logic is unchanged.

> Migrated from the previous SvelteKit portal, which is preserved at
> `../web-svelte-legacy/` (safe to delete once you're happy with this one).

## Run it (dev)

Two processes. **Backend** (from the repo root) — runtime connects as the
non-superuser `sopstore_app`, migrations as the owner (RLS enforced):

```bash
docker compose -f deploy/docker-compose.yml up -d db redis minio
# seed the dev login once: psql ... -f scripts/dev-seed.sql   (admin@example.com / admin)
DATABASE_USER=sopstore_app DATABASE_PASSWORD=sopstore_app \
FLYWAY_URL=jdbc:postgresql://localhost:5432/sopstore FLYWAY_USER=sopstore FLYWAY_PASSWORD=sopstore \
./gradlew bootRun -Pdevlog=off          # http://localhost:8080
```

**Portal**:

```bash
cd web
npm install
npm run dev                            # http://localhost:5173
```

Or just `scripts/dev.sh` from the repo root to run everything at once.

Open **http://localhost:5173** and sign in (dev login pre-filled). Vite proxies
`/api`, `/login`, `/logout`, `/actuator` to `:8080`, so the browser is
same-origin with the backend and the Spring session cookie + CSRF token work
without CORS. Point at a different backend with `SOPSTORE_API=http://host:port`.

## What's here

- `src/lib/api.ts` — fetch client (sends `X-XSRF-TOKEN` from the cookie on
  mutations; redirects to `/signin` on 401). Also `login`/`logout`/`upload`.
- `src/lib/toaster.ts` — Blueprint `OverlayToaster` wrapper (`toast(msg, intent)`).
- `src/lib/ui.ts` — `statusIntent()` (status → Blueprint Intent), `dt`, `dur`.
- `src/lib/app-context.ts` — `me` + badge counts shared via React context.
- `src/components/AppLayout.tsx` — authenticated shell (sidebar + Navbar + auth
  guard) wrapping the routed pages.
- `src/pages/` — `SignIn`, `Dashboard`, `Procedures` (list + create),
  `ProcedureDetail` (structured authoring: Purpose / Scope / Prerequisites /
  Steps + attachments + change control), `Approvals`, `Notifications`, `Runs`,
  `Settings` (admin-only — Configuration → prerequisite **types** and a reusable
  prerequisite **library**). Creating a procedure can attach prerequisites from
  the library or as custom lines.
- `src/App.tsx` — routes. `src/main.tsx` — entry (imports Blueprint CSS).

Procedures are authored as a structured document (Purpose, Scope, dynamic
Prerequisites, and Step cards). The document number is assigned automatically by
the backend (`SOP-0001`, `POL-0001`, … — per-tenant, per-type).

## Build & check

```bash
npm run typecheck   # tsc --noEmit
npm run build       # tsc --noEmit && vite build  →  dist/
npm run preview     # serve the production build locally
```
