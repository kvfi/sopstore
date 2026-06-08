-- Lifecycle: change requests, signatures, workflows.

CREATE TABLE change_request (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    procedure_id    uuid NOT NULL REFERENCES procedure(id) ON DELETE CASCADE,
    title           text NOT NULL,
    reason          text NOT NULL,
    classification  text NOT NULL CHECK (classification IN ('ADMINISTRATIVE','MINOR','MAJOR')),
    training_impact boolean NOT NULL DEFAULT false,
    status          text NOT NULL CHECK (status IN ('OPEN','IN_PROGRESS','APPROVED','REJECTED','CLOSED')),
    requested_by    uuid NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    created_at      timestamptz NOT NULL DEFAULT now(),
    closed_at       timestamptz
);
CREATE INDEX idx_cr_proc ON change_request(procedure_id, status);

CREATE TABLE signature (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    signer_id       uuid NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    subject_id      uuid NOT NULL,
    subject_type    text NOT NULL,
    meaning         text NOT NULL CHECK (meaning IN ('AUTHORED','REVIEWED','APPROVED','PUBLISHED','PERIODIC_REVIEWED','RETIRED')),
    payload_sha256  varchar(64) NOT NULL,
    reauth_token_id uuid NOT NULL,
    signed_at       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_sig_subject ON signature(subject_id, signed_at);

-- Workflow templates and instances. Stored shape is a thin record; the runtime
-- engine logic lives in the lifecycle.workflow package.
CREATE TABLE workflow (
    id          uuid PRIMARY KEY,
    tenant_id   uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    name        text NOT NULL,
    stages_json jsonb NOT NULL,
    UNIQUE (tenant_id, name)
);
CREATE TABLE workflow_instance (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    workflow_id     uuid NOT NULL REFERENCES workflow(id) ON DELETE RESTRICT,
    subject_id      uuid NOT NULL,
    status          text NOT NULL,
    started_at      timestamptz NOT NULL DEFAULT now(),
    completed_at    timestamptz
);

ALTER TABLE change_request    ENABLE ROW LEVEL SECURITY;
ALTER TABLE signature         ENABLE ROW LEVEL SECURITY;
ALTER TABLE workflow          ENABLE ROW LEVEL SECURITY;
ALTER TABLE workflow_instance ENABLE ROW LEVEL SECURITY;

CREATE POLICY cr_iso  ON change_request    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY sig_iso ON signature         USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY wf_iso  ON workflow          USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY wfi_iso ON workflow_instance USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
