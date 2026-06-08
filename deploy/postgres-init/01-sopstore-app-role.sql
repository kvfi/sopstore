-- Provisions the non-superuser runtime role for sopstore.
--
-- Postgres exempts superusers from row-level security entirely, and a table's owner
-- unless the table is FORCEd (see security/V001). So for RLS to actually bite, the
-- application must connect as a plain, non-superuser, non-owner role. Migrations still
-- run as the owner (`sopstore`) — they need DDL + FORCE RLS privileges — while the
-- runtime datasource uses `sopstore_app`.
--
-- Runs at DB init (docker-entrypoint-initdb.d) as POSTGRES_USER before the app migrates,
-- so the ALTER DEFAULT PRIVILEGES below covers every table the owner creates afterwards.
-- Idempotent: safe to re-run.

DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'sopstore_app') THEN
    -- NOSUPERUSER + NOBYPASSRLS are the defaults; stated here for the audit record.
    CREATE ROLE sopstore_app LOGIN PASSWORD 'sopstore_app' NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
  END IF;
END $$;

GRANT CONNECT ON DATABASE sopstore TO sopstore_app;
GRANT USAGE ON SCHEMA public TO sopstore_app;

-- Existing objects (no-op on a fresh DB; covers re-runs against a migrated DB).
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO sopstore_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO sopstore_app;

-- Tables/sequences the owner creates during migration inherit these grants.
ALTER DEFAULT PRIVILEGES FOR ROLE sopstore IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO sopstore_app;
ALTER DEFAULT PRIVILEGES FOR ROLE sopstore IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO sopstore_app;
