-- Background/scheduled jobs run with NO tenant context, but the `tenant` registry
-- is itself under RLS (tenant_self_only: id = current_tenant_id()), so the runtime
-- role can't enumerate tenants to iterate per-tenant work.
--
-- This SECURITY DEFINER function — owned by the BYPASSRLS break-glass role — exposes
-- exactly one narrow read (active tenant ids) without granting the runtime role
-- BYPASSRLS itself. See PHASE-STATUS "per-tenant iteration in the escalation job".
--
-- BYPASSRLS skips row policies but not table privileges, so the definer role still
-- needs schema USAGE + SELECT on the registry table. Best-effort: requires the migrator
-- to own these (it does in dev/prod, where migrations run as the schema owner). Skipped
-- in tests that migrate as the runtime role, which never call the function.
DO $$ BEGIN
    GRANT USAGE ON SCHEMA public TO sopstore_bypass_rls;
    GRANT SELECT ON tenant TO sopstore_bypass_rls;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'active_tenant_ids(): could not grant registry read to bypass role (%); enumeration may be limited', SQLERRM;
END $$;

CREATE OR REPLACE FUNCTION active_tenant_ids()
    RETURNS SETOF uuid
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = public
AS $$
    SELECT id FROM tenant WHERE status = 'ACTIVE'
$$;

-- Execute with the bypass role's privileges so tenant_self_only is bypassed for this
-- read. Best-effort: needs the migrator to be a member of (or own) the bypass role;
-- it is in dev/test (superuser). If it fails, the function still works wherever the
-- migrator already bypasses RLS — log and continue rather than block startup.
DO $$ BEGIN
    ALTER FUNCTION active_tenant_ids() OWNER TO sopstore_bypass_rls;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'active_tenant_ids(): could not set owner to sopstore_bypass_rls (%); cross-tenant enumeration may be limited', SQLERRM;
END $$;

REVOKE ALL ON FUNCTION active_tenant_ids() FROM PUBLIC;

-- The runtime role is provisioned outside migrations (deploy/postgres-init); grant only if present.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sopstore_app') THEN
        GRANT EXECUTE ON FUNCTION active_tenant_ids() TO sopstore_app;
    END IF;
END $$;
