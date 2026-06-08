-- Procedures, versions, steps, attachments, categories, tags, templates.

CREATE TABLE category (
    id        uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    name      text NOT NULL,
    UNIQUE (tenant_id, name)
);

CREATE TABLE procedure (
    id                  uuid PRIMARY KEY,
    tenant_id           uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    document_number     text NOT NULL,
    title               text NOT NULL,
    type                text NOT NULL CHECK (type IN ('POLICY','SOP','WORK_INSTRUCTION','FORM','JOB_AID')),
    category_id         uuid REFERENCES category(id) ON DELETE SET NULL,
    owner_id            uuid NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    current_version_id  uuid,
    state               text NOT NULL DEFAULT 'DRAFT',
    effective_date      date,
    next_review_date    date,
    retired_at          timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, document_number)
);
CREATE INDEX idx_procedure_state ON procedure(tenant_id, state);

CREATE TABLE procedure_version (
    id                  uuid PRIMARY KEY,
    tenant_id           uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    procedure_id        uuid NOT NULL REFERENCES procedure(id) ON DELETE CASCADE,
    version_major       int  NOT NULL,
    version_minor       int  NOT NULL,
    body_json           jsonb NOT NULL DEFAULT '{}'::jsonb,
    summary             text,
    change_request_id   uuid,
    created_by          uuid NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    created_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (procedure_id, version_major, version_minor)
);

CREATE TABLE procedure_step (
    id                   uuid PRIMARY KEY,
    tenant_id            uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    procedure_version_id uuid NOT NULL REFERENCES procedure_version(id) ON DELETE CASCADE,
    parent_step_id       uuid REFERENCES procedure_step(id) ON DELETE CASCADE,
    order_index          int  NOT NULL,
    title                text NOT NULL,
    instruction          text NOT NULL,
    expected_outcome     text,
    warning              text,
    evidence_spec        jsonb,
    estimated_minutes    int,
    responsible_role     text
);
CREATE INDEX idx_step_pv ON procedure_step(procedure_version_id, order_index);

CREATE TABLE attachment (
    id                   uuid PRIMARY KEY,
    tenant_id            uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    procedure_version_id uuid NOT NULL REFERENCES procedure_version(id) ON DELETE CASCADE,
    storage_key          text NOT NULL,
    mime                 text NOT NULL,
    size_bytes           bigint NOT NULL,
    sha256               varchar(64) NOT NULL,
    uploaded_at          timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE tag (
    id        uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    name      text NOT NULL,
    UNIQUE (tenant_id, name)
);
CREATE TABLE procedure_tag (
    procedure_id uuid NOT NULL REFERENCES procedure(id) ON DELETE CASCADE,
    tag_id       uuid NOT NULL REFERENCES tag(id) ON DELETE CASCADE,
    PRIMARY KEY (procedure_id, tag_id)
);

-- Postgres FTS: tsvector materialised column on procedure title + body.
ALTER TABLE procedure_version
    ADD COLUMN search_vec tsvector
    GENERATED ALWAYS AS (to_tsvector('simple', body_json::text)) STORED;
CREATE INDEX idx_pv_search ON procedure_version USING gin (search_vec);

-- RLS.
ALTER TABLE category          ENABLE ROW LEVEL SECURITY;
ALTER TABLE procedure         ENABLE ROW LEVEL SECURITY;
ALTER TABLE procedure_version ENABLE ROW LEVEL SECURITY;
ALTER TABLE procedure_step    ENABLE ROW LEVEL SECURITY;
ALTER TABLE attachment        ENABLE ROW LEVEL SECURITY;
ALTER TABLE tag               ENABLE ROW LEVEL SECURITY;

CREATE POLICY cat_iso ON category          USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY proc_iso ON procedure        USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY pv_iso   ON procedure_version USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY step_iso ON procedure_step   USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY att_iso  ON attachment       USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY tag_iso  ON tag              USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
