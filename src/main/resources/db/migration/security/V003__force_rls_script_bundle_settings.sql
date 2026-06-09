-- FORCE row-level security on tables added after the earlier sweeps (the app connects as the table
-- owner, which Postgres otherwise exempts from RLS). Re-running the generic sweep is safe: FORCE on
-- an already-forced table is a no-op. This keeps the script_bundle_settings table — and any other
-- newly RLS-enabled table — protected for the owner role on databases already past V001/V002.
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
      AND NOT c.relforcerowsecurity
      AND n.nspname = 'public'
  LOOP
    EXECUTE format(
      'ALTER TABLE %I.%I FORCE ROW LEVEL SECURITY', rel.schema_name, rel.table_name);
  END LOOP;
END $$;
