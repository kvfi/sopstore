-- Per-tenant settings that control how a procedure's RUN_SCRIPT scripts are named and linked in the
-- exported SOP bundle (bundle.zip) and the PDF. Exactly one row per tenant (the UNIQUE on tenant_id),
-- created lazily the first time an admin saves the configuration; absent rows fall back to the
-- application defaults baked into ScriptBundleNaming.
--
--   bundle_folder    folder the script files live in inside the zip (e.g. "scripts/")
--   filename_pattern token template for each script file's name; tokens: {code} {name} {version} {ext}
--   bundle_name      token template for the downloaded zip's filename; tokens: {document}
--   link_base_url    optional absolute prefix for the PDF's script hyperlinks; blank = relative path
CREATE TABLE script_bundle_settings (
    id               uuid PRIMARY KEY,
    tenant_id        uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    bundle_folder    text NOT NULL DEFAULT 'scripts/',
    filename_pattern text NOT NULL DEFAULT '{code}_{name}_{version}.{ext}',
    bundle_name      text NOT NULL DEFAULT '{document}-bundle.zip',
    link_base_url    text NOT NULL DEFAULT '',
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id)
);

-- Tenant isolation, consistent with the other procedure-module tables. The security module's
-- migration additionally FORCEs RLS for the owner role (see security/V002).
ALTER TABLE script_bundle_settings ENABLE ROW LEVEL SECURITY;
CREATE POLICY script_bundle_settings_iso ON script_bundle_settings
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
