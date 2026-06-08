-- Security hardening: make row-level security apply to the table-owner role too.
--
-- Postgres exempts a table's owner from RLS unless the table is set to FORCE, and
-- the application connects as the owner — so without this the per-module
-- `ENABLE ROW LEVEL SECURITY` policies are inert for the app. This migration runs
-- last (its Flyway location is ordered after every module's, so all tenant-scoped
-- tables already exist) and forces RLS on every table that has it enabled.
--
-- The break-glass `sopstore_bypass_rls` role keeps BYPASSRLS for tenant
-- onboarding and cross-tenant administration.
DO $$
DECLARE
  rel record;
BEGIN
  FOR rel IN
    SELECT n.nspname AS schema_name, c.relname AS table_name
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relkind = 'r'
      AND c.relrowsecurity
      AND n.nspname = 'public'
  LOOP
    EXECUTE format(
      'ALTER TABLE %I.%I FORCE ROW LEVEL SECURITY', rel.schema_name, rel.table_name);
  END LOOP;
END $$;
