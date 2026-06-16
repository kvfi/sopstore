-- Tenant-managed custom procedure-form schema: extra sections (rendered after the built-in
-- Purpose/Scope/Prerequisites/Steps) and their fields. Authors fill the fields per procedure; the
-- values plus a snapshot of the field definitions are stored in the procedure body JSON, so editing
-- or deleting the schema here never corrupts existing (especially published) procedure versions.

CREATE TABLE procedure_section (
    id         uuid PRIMARY KEY,
    tenant_id  uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    title      text NOT NULL,
    sort_order integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE procedure_field (
    id         uuid PRIMARY KEY,
    tenant_id  uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    section_id uuid NOT NULL REFERENCES procedure_section(id) ON DELETE CASCADE,
    label      text NOT NULL,
    type       text NOT NULL DEFAULT 'TEXT',
    options    text NOT NULL DEFAULT '',
    required   boolean NOT NULL DEFAULT false,
    sort_order integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_procedure_field_section ON procedure_field (section_id, sort_order);

-- Tenant isolation, consistent with the other procedure-module tables. The security module's final
-- migration additionally FORCEs RLS for the owner role.
ALTER TABLE procedure_section ENABLE ROW LEVEL SECURITY;
CREATE POLICY procedure_section_iso ON procedure_section
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE procedure_field ENABLE ROW LEVEL SECURITY;
CREATE POLICY procedure_field_iso ON procedure_field
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
