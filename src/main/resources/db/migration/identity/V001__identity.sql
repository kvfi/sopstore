-- Identity: users, groups, roles, MFA credentials.
-- Every table tenant-scoped; RLS bound to current_tenant_id().

CREATE TABLE app_user (
    id            uuid PRIMARY KEY,
    tenant_id     uuid NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    email         text NOT NULL,
    display_name  text NOT NULL,
    employee_id   text,
    password_hash text,
    locale        text NOT NULL DEFAULT 'en',
    timezone      text NOT NULL DEFAULT 'UTC',
    status        text NOT NULL CHECK (status IN ('ACTIVE','INVITED','SUSPENDED','DELETED')),
    mfa_required  boolean NOT NULL DEFAULT false,
    created_at    timestamptz NOT NULL DEFAULT now(),
    deleted_at    timestamptz,
    CONSTRAINT uq_user_email UNIQUE (tenant_id, email)
);
CREATE INDEX idx_user_emp ON app_user(tenant_id, employee_id);

CREATE TABLE user_role (
    user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role    text NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE user_group (
    id        uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    name      text NOT NULL,
    UNIQUE (tenant_id, name)
);

CREATE TABLE user_group_member (
    group_id uuid NOT NULL REFERENCES user_group(id) ON DELETE CASCADE,
    user_id  uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, user_id)
);

CREATE TABLE mfa_credential (
    id                    uuid PRIMARY KEY,
    tenant_id             uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    user_id               uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    type                  text NOT NULL CHECK (type IN ('TOTP','WEBAUTHN')),
    secret_or_credential  bytea NOT NULL,
    label                 text NOT NULL,
    created_at            timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_mfa_user ON mfa_credential(user_id, type);

-- RLS
ALTER TABLE app_user       ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_group     ENABLE ROW LEVEL SECURITY;
ALTER TABLE mfa_credential ENABLE ROW LEVEL SECURITY;

CREATE POLICY user_iso  ON app_user       USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY group_iso ON user_group     USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY mfa_iso   ON mfa_credential USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
