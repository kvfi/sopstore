# Design spec — sopstore professional UI refresh

Date: 2026-06-14
Status: approved (verbal), implementation in progress

## Goal

Give the sopstore React SPA a professional, cohesive look — centered on a
redesigned Dashboard — without rewriting the component framework (Blueprint 6).

## Decisions (from brainstorming)

- **Scope:** Full app visual overhaul (tokens + shell + Dashboard + shared
  tables/cards/page headers rippling across all pages).
- **Charts:** Yes — add **Recharts** (lightweight). Core charts derive from data
  the `/api/v1/dashboard` endpoint **already returns** → no backend change.
- **Aesthetic:** Refined enterprise (light theme). Sidebar stays light.
- **Approach:** Design-token CSS layer + reusable React primitives, staying
  inside Blueprint. (Rejected: Tailwind-alongside-Blueprint; full shadcn rewrite.)

## New dependencies

- `recharts` — charts.
- `@fontsource/inter` — self-hosted Inter (no CDN; safe for `onprem` profile).

## Work breakdown

1. **Design tokens** — `web/src/styles/tokens.css`: slate neutral scale, primary
   blue scale around `#215db0`, semantic status colors (aligned to Blueprint
   intents), type scale (Inter UI + IBM Plex Mono), 3 shadow levels, spacing &
   radius scale. Remap existing `--bp-*` vars onto tokens so current code keeps
   working. Import Inter + tokens in `main.tsx`.
2. **Shell** — restyle sidebar (pill + accent-bar active state, refined brand,
   user card) and topbar (title + search + notifications + profile menu) in
   `AppLayout.tsx` + `app.css`.
3. **Shared primitives** — `PageHeader` (title/subtitle/actions), `Panel`
   (titled card + action slot), `StatCard` (icon/value/label/accent),
   `data-table` styling. Under `web/src/components/`.
4. **Dashboard** (`pages/Dashboard.tsx`) — bento layout:
   - Stat row: 4 `StatCard`s from `kpis` (danger tint when count > 0).
   - Charts row: Compliance posture donut/gauge, Change-requests-by-status
     donut, Deviations-by-category bar — all derived from existing arrays.
   - Work queues: 4 existing tables → titled `Panel`s in a 2-col grid with
     "View all →" links, hover rows, nicer empty states, scroll caps.
5. **Rollout** — apply `PageHeader` / `Panel` / `data-table` across Procedures,
   Runs, Approvals, Notifications, Settings for consistency.

## Out of scope / stretch

- **Trend-line endpoint** (`GET /api/v1/dashboard/trends`, reporting module) for
  true time-series area charts — deferred; dashboard is complete without it.
- No dark mode, no SSR, no backend changes in core scope.

## Verification

- `cd web && npm run typecheck && npm run build` stay green.
- Visual check against `scripts/dev.sh` stack (admin@example.com / admin).
- Backend gates unaffected (no backend changes in core scope).
