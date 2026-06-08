-- Tenant-defined Word-export themes: name, accent colour, footer text, and an optional logo image.
-- A procedure selects one of its tenant's templates (stored in the version body JSON) for export.
CREATE TABLE doc_template (
    id           uuid PRIMARY KEY,
    tenant_id    uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    name         text NOT NULL,
    accent_color varchar(16) NOT NULL DEFAULT '215db0',
    footer_text  text,
    logo         bytea NOT NULL DEFAULT '\x',
    logo_mime    varchar(100),
    created_at   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, name)
);

-- Tenant isolation, consistent with the other procedure-module tables. FORCE is applied here too
-- (the security module's blanket FORCE migration already ran, so a new table must force its own).
ALTER TABLE doc_template ENABLE ROW LEVEL SECURITY;
ALTER TABLE doc_template FORCE ROW LEVEL SECURITY;
CREATE POLICY doc_template_iso ON doc_template
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
