-- Tenant-managed catalogue of confidentiality levels (e.g. "Public", "Internal",
-- "Confidential", "Restricted"). Ordered by rank (lower is less sensitive). A procedure may be
-- classified with one; the chosen level is marked on the exported PDF. Admins manage the list.
CREATE TABLE confidentiality_level (
    id         uuid PRIMARY KEY,
    tenant_id  uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    name       text NOT NULL,
    rank       integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, name)
);

-- Tenant isolation, consistent with the other procedure-module tables. The security module's
-- migration additionally FORCEs RLS for the owner role (see security/V002).
ALTER TABLE confidentiality_level ENABLE ROW LEVEL SECURITY;
CREATE POLICY confidentiality_level_iso ON confidentiality_level
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
