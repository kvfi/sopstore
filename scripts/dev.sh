#!/usr/bin/env bash
#
# dev.sh — one command to run the full local stack: Docker infra + Spring
# backend (:8080) + React/Blueprint admin portal (:5173).
#
# The portal's Vite dev server proxies /api, /login, /logout and /actuator to
# the backend, so you only ever open http://localhost:5173.
#
# Usage:
#   scripts/dev.sh                 # infra + backend + script-service + portal (recommended)
#   scripts/dev.sh --no-infra      # assume db/redis/minio/script-db are already up
#   scripts/dev.sh --no-seed       # skip seeding the dev admin login
#   scripts/dev.sh --no-scripts    # skip the standalone script-service (:8090)
#   scripts/dev.sh --backend-only  # just the Spring app + script-service (+ infra/seed)
#   scripts/dev.sh --frontend-only # just the portal (backend must be running)
#
# Press Ctrl-C once to stop everything this script started. Docker infra is
# left running on purpose (fast restart); stop it with:
#   docker compose -f deploy/docker-compose.yml down
#
set -euo pipefail

# --- Resolve repo root (this script lives in <root>/scripts) -----------------
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

COMPOSE_FILE="deploy/docker-compose.yml"
BACKEND_URL="http://localhost:8080"
SCRIPTS_URL="http://localhost:8090"
PORTAL_URL="http://localhost:5173"

# --- Options -----------------------------------------------------------------
RUN_INFRA=1 RUN_SEED=1 RUN_BACKEND=1 RUN_SCRIPTS=1 RUN_FRONTEND=1
for arg in "$@"; do
  case "$arg" in
    --no-infra)      RUN_INFRA=0 ;;
    --no-seed)       RUN_SEED=0 ;;
    --no-scripts)    RUN_SCRIPTS=0 ;;
    --backend-only)  RUN_FRONTEND=0 ;;
    --frontend-only) RUN_INFRA=0; RUN_SEED=0; RUN_BACKEND=0; RUN_SCRIPTS=0 ;;
    -h|--help)       sed -n '2,21p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "dev.sh: unknown option '$arg' (try --help)" >&2; exit 2 ;;
  esac
done

# --- Pretty logging ----------------------------------------------------------
c_blue=$'\033[34m'; c_green=$'\033[32m'; c_dim=$'\033[2m'; c_off=$'\033[0m'
say()  { printf '%s==>%s %s\n' "$c_blue" "$c_off" "$*"; }
need() { command -v "$1" >/dev/null 2>&1 || { echo "dev.sh: '$1' not found on PATH" >&2; exit 1; }; }

# --- Prerequisite checks -----------------------------------------------------
[ "$RUN_INFRA" = 1 ] && need docker
[ "$RUN_BACKEND" = 1 ] && need java
[ "$RUN_FRONTEND" = 1 ] && need npm

# --- Child process bookkeeping + clean shutdown ------------------------------
# We track each service's top PID. bootRun forks an app JVM and npm forks vite,
# so on shutdown we must kill the whole process *tree*, not just the top PID —
# otherwise the JVM / node survive (the bug this replaces).
PIDS=()

# Recursively signal a process and all its descendants, leaves first.
kill_tree() {
  local sig="$1" pid="$2" child
  while read -r child; do
    [ -n "$child" ] && kill_tree "$sig" "$child"
  done < <(pgrep -P "$pid" 2>/dev/null || true)
  kill "-$sig" "$pid" 2>/dev/null || true
}

cleanup() {
  trap - INT TERM EXIT
  say "shutting down…"
  for pid in "${PIDS[@]:-}"; do [ -n "${pid:-}" ] && kill_tree TERM "$pid"; done
  sleep 1.5  # let the JVM / node shut down gracefully
  for pid in "${PIDS[@]:-}"; do [ -n "${pid:-}" ] && kill_tree KILL "$pid"; done
  wait 2>/dev/null || true
  say "stopped."
}
trap cleanup INT TERM EXIT

# Stream a child's output with a coloured [label] prefix.
prefix() { local label="$1" color="$2"; sed -u "s/^/${color}[${label}]${c_off} /"; }

# Launch a service in the background, prefixing its output. Critically uses a
# process substitution (not a pipe) so $! is the service itself, not the sed.
launch() {
  local label="$1" color="$2"; shift 2
  "$@" > >(prefix "$label" "$color") 2>&1 &
  PIDS+=("$!")
}

# --- 1. Docker infra ---------------------------------------------------------
# Wait for a compose container's healthcheck to report `healthy` (or die trying).
wait_healthy() {
  local container="$1" label="$2" status
  say "waiting for $label to be healthy…"
  for i in $(seq 1 60); do
    status="$(docker inspect -f '{{.State.Health.Status}}' "$container" 2>/dev/null || echo starting)"
    [ "$status" = healthy ] && break
    [ "$i" = 60 ] && { echo "dev.sh: $label did not become healthy in time" >&2; exit 1; }
    sleep 1
  done
  say "$label is healthy."
}

if [ "$RUN_INFRA" = 1 ]; then
  # script-db is only needed when we also run the script-service.
  infra_services="db redis minio"
  [ "$RUN_SCRIPTS" = 1 ] && infra_services="$infra_services script-db"
  say "starting infra ($infra_services)…"
  docker compose -f "$COMPOSE_FILE" up -d $infra_services

  wait_healthy sopstore-db postgres
  [ "$RUN_SCRIPTS" = 1 ] && wait_healthy sopstore-script-db script-db
fi

# --- 2. Backend (Spring Boot, :8080) -----------------------------------------
# Runtime connects as the non-superuser sopstore_app (RLS enforced); Flyway
# runs migrations as the owner sopstore. See web/README.md and ADR-0007.
if [ "$RUN_BACKEND" = 1 ]; then
  say "starting backend → $BACKEND_URL ${c_dim}(./gradlew --no-daemon bootRun)${c_off}"
  # --no-daemon keeps the app JVM a child of this gradle process (not the shared
  # daemon), so Ctrl-C / kill_tree actually terminates it.
  launch backend "$c_green" \
    env \
      DATABASE_USER=sopstore_app DATABASE_PASSWORD=sopstore_app \
      FLYWAY_URL=jdbc:postgresql://localhost:5432/sopstore \
      FLYWAY_USER=sopstore FLYWAY_PASSWORD=sopstore \
      ./gradlew --no-daemon bootRun -Pdevlog=off --console=plain

  # Wait for the app (and thus Flyway migrations) before seeding / starting web.
  say "waiting for backend health…"
  for i in $(seq 1 120); do
    if curl -fsS "$BACKEND_URL/actuator/health" >/dev/null 2>&1; then break; fi
    # If gradle died, stop pretending we're waiting.
    kill -0 "${PIDS[-1]}" 2>/dev/null || { echo "dev.sh: backend exited during startup" >&2; exit 1; }
    [ "$i" = 120 ] && { echo "dev.sh: backend did not become healthy in time" >&2; exit 1; }
    sleep 2
  done
  say "backend is up."
fi

# --- 3. Seed the dev admin login (idempotent) --------------------------------
# Tables only exist after the backend has run Flyway migrations, so this comes
# after the health check. Credentials: admin@example.com / admin
if [ "$RUN_SEED" = 1 ] && [ "$RUN_BACKEND" = 1 ]; then
  if docker ps --format '{{.Names}}' | grep -q '^sopstore-db$'; then
    say "seeding dev login ${c_dim}(admin@example.com / admin)${c_off}"
    docker exec -i sopstore-db psql -q -U sopstore -d sopstore \
      < scripts/dev-seed.sql && say "seed applied." \
      || echo "dev.sh: seeding failed (continuing anyway)" >&2
  fi
fi

# --- 3b. Script-service (standalone Spring Boot app, :8090) -------------------
# Separate, independently-deployable app (ADR-0009) with its own DB (script-db
# on :5433) and Flyway. sopstore's `scripts` module proxies the SPA to it; the
# shared X-Service-Token stays server-side. Defaults match application.yml.
if [ "$RUN_SCRIPTS" = 1 ]; then
  say "starting script-service → $SCRIPTS_URL ${c_dim}(./gradlew --no-daemon :script-service:bootRun)${c_off}"
  launch script-service "$c_green" \
    env \
      SCRIPT_DB_URL=jdbc:postgresql://localhost:5433/scriptstore \
      SCRIPT_DB_USER=scriptstore SCRIPT_DB_PASSWORD=scriptstore \
      SCRIPT_SERVICE_TOKEN=dev-script-token \
      ./gradlew --no-daemon :script-service:bootRun --console=plain

  say "waiting for script-service health…"
  for i in $(seq 1 120); do
    if curl -fsS "$SCRIPTS_URL/actuator/health" >/dev/null 2>&1; then break; fi
    kill -0 "${PIDS[-1]}" 2>/dev/null || { echo "dev.sh: script-service exited during startup" >&2; exit 1; }
    [ "$i" = 120 ] && { echo "dev.sh: script-service did not become healthy in time" >&2; exit 1; }
    sleep 2
  done
  say "script-service is up."
fi

# --- 4. Frontend (React/Blueprint portal, :5173) -----------------------------
if [ "$RUN_FRONTEND" = 1 ]; then
  if [ ! -d web/node_modules ]; then
    say "installing portal dependencies ${c_dim}(npm install)${c_off}"
    ( cd web && npm install )
  fi
  say "starting portal → $PORTAL_URL ${c_dim}(npm run dev)${c_off}"
  # exec so npm replaces the bash shell — keeps the process tree shallow for kill_tree.
  launch web "$c_blue" bash -c 'cd web && exec npm run dev'
fi

say "stack is up. Open ${PORTAL_URL}  —  Ctrl-C to stop."
[ "$RUN_SCRIPTS" = 1 ] && say "script-service on ${SCRIPTS_URL} ${c_dim}(/scripts features enabled)${c_off}"

# Block until a child exits (or Ctrl-C); cleanup() handles the rest.
wait
