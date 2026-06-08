-- Tenant-managed catalogue of prerequisite types (e.g. "Equipment / tool",
-- "Training / qualification"). Authors pick one per prerequisite; admins manage
-- the list. The chosen type's name is snapshotted into the procedure body JSON,
-- so deleting a type never corrupts existing procedures.
CREATE TABLE prerequisite_type (
    id         uuid PRIMARY KEY,
    tenant_id  uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    name       text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, name)
);

-- Tenant isolation, consistent with the other procedure-module tables. The
-- security module's final migration additionally FORCEs RLS for the owner role.
ALTER TABLE prerequisite_type ENABLE ROW LEVEL SECURITY;
CREATE POLICY prereq_type_iso ON prerequisite_type
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
