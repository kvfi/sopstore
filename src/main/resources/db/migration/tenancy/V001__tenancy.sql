-- Tenancy: tenant + org hierarchy.
-- RLS template: every tenant-scoped table uses identical policy shape.

CREATE TABLE tenant (
    id          uuid PRIMARY KEY,
    name        text NOT NULL,
    slug        text NOT NULL UNIQUE,
    status      text NOT NULL CHECK (status IN ('ACTIVE','SUSPENDED','ARCHIVED')),
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE org_unit (
    id          uuid PRIMARY KEY,
    tenant_id   uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    level       text NOT NULL CHECK (level IN ('ORGANIZATION','BUSINESS_UNIT','SITE','DEPARTMENT','TEAM')),
    name        text NOT NULL,
    parent_id   uuid REFERENCES org_unit(id) ON DELETE SET NULL,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_org_unit_tenant ON org_unit(tenant_id);
CREATE INDEX idx_org_unit_parent ON org_unit(parent_id);

-- RLS on tenant-scoped tables.
ALTER TABLE org_unit ENABLE ROW LEVEL SECURITY;
CREATE POLICY org_unit_tenant_isolation ON org_unit
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- 'tenant' itself is the registry; readable in a tenant-bound context only
-- when id matches. Admin / break-glass code uses the bypass role.
ALTER TABLE tenant ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_self_only ON tenant
    USING (id = current_tenant_id())
    WITH CHECK (id = current_tenant_id());
