-- Training & competency.

CREATE TABLE curriculum (
    id          uuid PRIMARY KEY,
    tenant_id   uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    name        text NOT NULL,
    role_target text,
    UNIQUE (tenant_id, name)
);
CREATE TABLE curriculum_item (
    curriculum_id uuid NOT NULL REFERENCES curriculum(id) ON DELETE CASCADE,
    procedure_id  uuid NOT NULL REFERENCES procedure(id)  ON DELETE CASCADE,
    PRIMARY KEY (curriculum_id, procedure_id)
);

CREATE TABLE training_assignment (
    id                   uuid PRIMARY KEY,
    tenant_id            uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    user_id              uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    procedure_id         uuid NOT NULL REFERENCES procedure(id) ON DELETE CASCADE,
    procedure_version_id uuid REFERENCES procedure_version(id) ON DELETE SET NULL,
    source               text NOT NULL CHECK (source IN ('AUTOMATIC_HIRE','AUTOMATIC_ROLE_CHANGE','AUTOMATIC_VERSION_CHANGE','MANUAL')),
    state                text NOT NULL CHECK (state IN ('PENDING','COMPLETED','OVERDUE','REVOKED')),
    due_at               date,
    assigned_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_ta_user_state ON training_assignment(user_id, state);
CREATE INDEX idx_ta_proc       ON training_assignment(procedure_id);

CREATE TABLE training_completion (
    id             uuid PRIMARY KEY,
    tenant_id      uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    assignment_id  uuid NOT NULL REFERENCES training_assignment(id) ON DELETE CASCADE,
    user_id        uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    version_sha256 varchar(64) NOT NULL,
    witnessed_by   uuid REFERENCES app_user(id) ON DELETE SET NULL,
    completed_at   timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE quiz (
    id             uuid PRIMARY KEY,
    tenant_id      uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    procedure_id   uuid NOT NULL REFERENCES procedure(id) ON DELETE CASCADE,
    pass_mark_pct  int  NOT NULL,
    max_attempts   int  NOT NULL,
    questions_json jsonb NOT NULL
);

CREATE TABLE quiz_attempt (
    id           uuid PRIMARY KEY,
    tenant_id    uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    quiz_id      uuid NOT NULL REFERENCES quiz(id) ON DELETE CASCADE,
    user_id      uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    score_pct    int  NOT NULL,
    answers_json jsonb NOT NULL,
    attempted_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE qualification (
    id           uuid PRIMARY KEY,
    tenant_id    uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    user_id      uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    procedure_id uuid NOT NULL REFERENCES procedure(id) ON DELETE CASCADE,
    qualified_on date NOT NULL,
    expires_on   date,
    trainer_id   uuid REFERENCES app_user(id) ON DELETE SET NULL,
    UNIQUE (user_id, procedure_id)
);

ALTER TABLE curriculum          ENABLE ROW LEVEL SECURITY;
ALTER TABLE training_assignment ENABLE ROW LEVEL SECURITY;
ALTER TABLE training_completion ENABLE ROW LEVEL SECURITY;
ALTER TABLE quiz                ENABLE ROW LEVEL SECURITY;
ALTER TABLE quiz_attempt        ENABLE ROW LEVEL SECURITY;
ALTER TABLE qualification       ENABLE ROW LEVEL SECURITY;

CREATE POLICY curr_iso ON curriculum          USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY ta_iso   ON training_assignment USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY tc_iso   ON training_completion USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY qz_iso   ON quiz                USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY qa_iso   ON quiz_attempt        USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY ql_iso   ON qualification       USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
