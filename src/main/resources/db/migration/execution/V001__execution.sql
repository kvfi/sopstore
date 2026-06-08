-- Execution: runs, evidence, deviations.

CREATE TABLE procedure_run (
    id                   uuid PRIMARY KEY,
    tenant_id            uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    procedure_id         uuid NOT NULL REFERENCES procedure(id) ON DELETE CASCADE,
    procedure_version_id uuid NOT NULL REFERENCES procedure_version(id) ON DELETE CASCADE,
    operator_id          uuid NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    state                text NOT NULL CHECK (state IN ('IN_PROGRESS','COMPLETED','ABANDONED')),
    started_at           timestamptz NOT NULL DEFAULT now(),
    completed_at         timestamptz
);
CREATE INDEX idx_run_op_state ON procedure_run(operator_id, state);

CREATE TABLE run_step_state (
    id          uuid PRIMARY KEY,
    tenant_id   uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    run_id      uuid NOT NULL REFERENCES procedure_run(id) ON DELETE CASCADE,
    step_id     uuid NOT NULL REFERENCES procedure_step(id) ON DELETE CASCADE,
    completed   boolean NOT NULL DEFAULT false,
    skipped     boolean NOT NULL DEFAULT false,
    completed_at timestamptz,
    UNIQUE (run_id, step_id)
);

CREATE TABLE evidence_item (
    id             uuid PRIMARY KEY,
    tenant_id      uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    run_id         uuid NOT NULL REFERENCES procedure_run(id) ON DELETE CASCADE,
    step_id        uuid NOT NULL REFERENCES procedure_step(id) ON DELETE CASCADE,
    kind           text NOT NULL CHECK (kind IN ('TEXT','MEASUREMENT','PHOTO','FILE','SIGNATURE','GPS','CHECKBOX','SELECTION')),
    text_value     text,
    numeric_value  numeric(30,10),
    unit           varchar(32),
    storage_key    text,
    mime           text,
    extra          jsonb,
    captured_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_ev_run_step ON evidence_item(run_id, step_id);

CREATE TABLE deviation (
    id                uuid PRIMARY KEY,
    tenant_id         uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    run_id            uuid NOT NULL REFERENCES procedure_run(id) ON DELETE CASCADE,
    step_id           uuid NOT NULL REFERENCES procedure_step(id) ON DELETE CASCADE,
    category          text NOT NULL CHECK (category IN ('OUT_OF_TOLERANCE','STEP_SKIPPED','EQUIPMENT_FAILURE','OPERATOR_ERROR','OTHER')),
    description       text NOT NULL,
    root_cause        text,
    corrective_action text,
    linked_capa_id    uuid,
    logged_at         timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_dev_run_step ON deviation(run_id, step_id);

ALTER TABLE procedure_run  ENABLE ROW LEVEL SECURITY;
ALTER TABLE run_step_state ENABLE ROW LEVEL SECURITY;
ALTER TABLE evidence_item  ENABLE ROW LEVEL SECURITY;
ALTER TABLE deviation      ENABLE ROW LEVEL SECURITY;

CREATE POLICY run_iso  ON procedure_run  USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY rss_iso  ON run_step_state USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY ev_iso   ON evidence_item  USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY dev_iso  ON deviation      USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
