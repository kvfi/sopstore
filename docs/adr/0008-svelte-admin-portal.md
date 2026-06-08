# ADR-0008: SvelteKit admin portal over a thin JSON API

- Status: **Accepted**
- Date: 2026-05-31
- Relates to: ADR-0007 (RLS credentials), platform SecurityConfig

## Context

The platform shipped a server-rendered Thymeleaf + HTMX UI. A richer,
app-like admin portal was wanted without rewriting the backend domain logic
(aggregates, workflow engine, RLS, Part 11 signatures).

## Decision

A standalone **SvelteKit 2 / Svelte 5 / Tailwind v4** SPA in `web/`, talking to
a **thin JSON API** layered over the existing services — no domain changes:

- New `@RestController`s under `/api/v1/*` (dashboard, procedures detail,
  change requests + approvals, runs, notifications, `/me`) each placed inside
  its owning Spring Modulith module so it can reach that module's internal
  service; cross-module reads go through the exposed `ProcedureApi` /
  `LifecycleApi` / `ExecutionApi`. DTOs only — no entities cross the wire.
- Auth reuses the existing session-cookie form login: a `successHandler` /
  `failureHandler` return `204`/`401` for XHR (Thymeleaf keeps its redirects),
  and `/api/**` gets a `401` entry point instead of a login redirect.
- CSRF stays on: `SpaCsrfTokenRequestHandler` lets the SPA send the raw
  `XSRF-TOKEN` cookie in the `X-XSRF-TOKEN` header while Thymeleaf forms keep
  masked tokens. A `CsrfCookieFilter` materializes the cookie up front.
- Dev: Vite proxies `/api`,`/login`,`/logout` to `:8080`, so the browser is
  same-origin with the backend (cookie + CSRF "just work", no CORS). A
  credentialed CORS config covers non-proxied/prod use. Prod build uses
  `adapter-node`.

## Rationale

- Keeps one Spring backend as the system of record; the SPA is a pure client.
- Same-origin dev proxy sidesteps the cross-origin cookie problem the "Node
  SSR" option otherwise carries.
- Module-local controllers respect the existing Modulith boundaries (verified).

## Verification

- Backend: `compileJava` + Checkstyle + SpotBugs + full Testcontainers suite green.
- End-to-end through the `:5173` proxy: unauth `/api/v1/me`→401, XHR login→204,
  then `/me`, `/dashboard`, `/approvals`, `/notifications`, `/runs`, procedure
  `/detail`, and the write path (create procedure → open change request →
  approval queue grows) all succeed. SvelteKit SSR renders the shell + pages.

## Consequences

- Two dev processes (Spring + Vite); production runs the SvelteKit node server
  alongside the JVM (or serves the static build behind the same origin).

## Update — 2026-05-31: Thymeleaf removed, API-only backend

The server-rendered Thymeleaf UI was deleted: all view `@Controller`s, the
`templates/` tree, the vendored static assets, the `spring-boot-starter-thymeleaf`
dependency, and the Node/Tailwind `frontendBuild` (Gradle `node` plugin) are gone.
The backend is now a pure JSON API. `GlobalExceptionHandler` returns JSON
(`@RestControllerAdvice`); form-login uses Spring's generated `/login` page (the SPA
owns the real login at `/signin` and POSTs to `/login`). **Swagger UI** is served at
`/swagger-ui/index.html`, OpenAPI at `/v3/api-docs` (springdoc; both `permitAll`).

Capabilities that existed *only* in Thymeleaf and now lack a UI (and, where noted, a
JSON API): **step authoring** (add/delete/reorder — no API), **training qualification**
grant/list (no API), and a **run-execution screen** (the start/measurement/note/complete
API exists via `RunsApiController`; the SPA only shows run history). These are the next
things to port into `web/`.
