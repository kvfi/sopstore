# sopstore admin portal (SvelteKit)

A single-page admin portal for the sopstore quality/SOP platform. SvelteKit 2 +
Svelte 5 + Tailwind v4, talking to the Spring backend's JSON API (`/api/v1/*`) —
the backend domain logic is unchanged.

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

Open **http://localhost:5173** and sign in (dev login pre-filled). Vite proxies
`/api`, `/login`, `/logout`, `/actuator` to `:8080`, so the browser is
same-origin with the backend and the Spring session cookie + CSRF token work
without CORS. Point at a different backend with `SOPSTORE_API=http://host:port`.

## What's here

- `src/routes/login` — sign-in (XHR form-login → 204/401).
- `src/routes/(app)` — authenticated shell (sidebar + top bar + auth guard) wrapping:
  Dashboard (`/`), Procedures (list + detail with steps/versions/change-control),
  Approvals (sign/reject with password), Notifications, Runs (history + analytics).
  Creating a procedure asks only for a type + title; the document number is
  assigned automatically by the backend (`SOP-0001`, `POL-0001`, … — a
  per-tenant, per-type counter).
- `src/lib/api.ts` — fetch client (sends `X-XSRF-TOKEN` from the cookie on
  mutations; redirects to `/login` on 401).
- `src/routes/layout.css` — the design system (Tailwind v4 `@theme` + components).

## Build

```bash
npm run build      # adapter-node output in build/  →  node build
```
