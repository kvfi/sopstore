-- Common baseline shared by every module.
-- Extensions, helper functions, and the bypass_rls role.

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
-- pgvector is optional; conditional create so air-gapped Postgres without the
-- extension still boots (semantic search disabled in that case).
DO $$ BEGIN
    CREATE EXTENSION IF NOT EXISTS vector;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'pgvector unavailable, semantic search disabled';
END $$;

-- Session GUC used by RLS policies.
DO $$ BEGIN
    PERFORM set_config('app.tenant_id', '00000000-0000-0000-0000-000000000000', false);
EXCEPTION WHEN OTHERS THEN NULL; END $$;

-- Helper for tenant-scoped tables.
CREATE OR REPLACE FUNCTION current_tenant_id() RETURNS uuid
LANGUAGE sql STABLE AS $$
    SELECT COALESCE(NULLIF(current_setting('app.tenant_id', true), ''), '00000000-0000-0000-0000-000000000000')::uuid
$$;

-- Role allowed to bypass RLS for break-glass admin / cross-tenant reporting.
-- Created idempotently; the application user must NOT be granted this role.
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sopstore_bypass_rls') THEN
        CREATE ROLE sopstore_bypass_rls NOLOGIN BYPASSRLS;
    END IF;
END $$;
