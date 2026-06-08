-- Tenant-managed library of reusable prerequisites. Each entry pairs a type
-- (snapshot of a prerequisite_type name) with its text. Authors pick these when
-- creating/authoring a procedure; the chosen {type, text} is snapshotted into the
-- procedure body, so editing or deleting a library entry never alters past procedures.
CREATE TABLE prerequisite (
    id         uuid PRIMARY KEY,
    tenant_id  uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    type       text NOT NULL,
    label      text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, type, label)
);

-- Tenant isolation, consistent with the other procedure-module tables. The
-- security module's final migration additionally FORCEs RLS for the owner role.
ALTER TABLE prerequisite ENABLE ROW LEVEL SECURITY;
CREATE POLICY prerequisite_iso ON prerequisite
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
