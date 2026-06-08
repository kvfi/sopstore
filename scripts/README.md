# Dev scripts

## `dev.sh` — run the whole stack with one command

Brings up the local Docker infra, the Spring backend, and the SvelteKit admin
portal together, then streams their logs side by side with `[backend]` / `[web]`
prefixes. Press **Ctrl-C once** to stop everything it started.

```bash
scripts/dev.sh
```

Then open **http://localhost:5173** and sign in (dev login pre-filled). The
portal's Vite dev server proxies `/api`, `/login`, `/logout` and `/actuator` to
the backend on `:8080`, so the browser is same-origin with Spring — the session
cookie and CSRF token work without CORS.

### What it does, in order

1. **Infra** — `docker compose up -d db redis minio`, then waits for Postgres to
   report healthy.
2. **Backend** — `./gradlew bootRun -Pdevlog=off` on **:8080**, with the
   split-credential env so the runtime connects as the non-superuser
   `sopstore_app` (RLS enforced) while Flyway migrates as the owner `sopstore`
   (see [ADR-0007] and `web/README.md`). Waits for `/actuator/health`.
3. **Seed** — applies `scripts/dev-seed.sql` (idempotent) once the migrations
   have created the tables. Login: **`admin@example.com` / `admin`**.
4. **Portal** — runs `npm install` if needed, then `npm run dev` on **:5173**.

### Options

| Flag | Effect |
| --- | --- |
| _(none)_ | infra + backend + seed + portal |
| `--no-infra` | assume `db`/`redis`/`minio` are already up |
| `--no-seed` | skip seeding the dev admin login |
| `--backend-only` | infra + backend + seed, no portal |
| `--frontend-only` | just the portal (a backend must already be running) |
| `-h`, `--help` | usage |

Point the portal at a different backend with `SOPSTORE_API=http://host:port`.

### Prerequisites

JDK 25 · Docker · Node/npm. The Gradle wrapper (9.5.1) is committed — no global
Gradle needed.

### Notes

- Docker infra is **left running** on exit (fast restart). Stop it with
  `docker compose -f deploy/docker-compose.yml down`.
- Running the two processes by hand instead is documented in
  [`web/README.md`](../web/README.md).

## `dev-seed.sql`

Local-only seed: an all-zeros "dev" tenant plus the `admin@example.com` admin
login (all roles). Re-runnable and safe after a schema reset. Applied
automatically by `dev.sh`; to run it by hand:

```bash
docker exec -i sopstore-db psql -U sopstore -d sopstore < scripts/dev-seed.sql
```

[ADR-0007]: ../docs/adr/0007-split-db-credentials-for-rls.md
