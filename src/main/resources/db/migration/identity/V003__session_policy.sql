-- Per-tenant session lifetime policy: idle timeout (seconds) and absolute timeout (seconds).
-- Exactly one row per tenant; when absent the platform defaults apply. RLS isolates rows per
-- tenant via current_tenant_id(); the security migration additionally FORCEs RLS for the owner role.
CREATE TABLE session_policy (
    id                       uuid PRIMARY KEY,
    tenant_id                uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    idle_timeout_seconds     integer NOT NULL DEFAULT 1800,
    absolute_timeout_seconds integer NOT NULL DEFAULT 43200,
    created_at               timestamptz NOT NULL DEFAULT now(),
    updated_at               timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id)
);

ALTER TABLE session_policy ENABLE ROW LEVEL SECURITY;
CREATE POLICY session_policy_iso ON session_policy
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
