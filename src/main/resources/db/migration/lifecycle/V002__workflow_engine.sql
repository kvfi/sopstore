-- Workflow engine: configurable multi-stage approval with per-stage e-signature tasks.
-- Extends the V001 workflow / workflow_instance shells with the runtime task table and the
-- instance bookkeeping (current stage pointer + originating change request) the engine needs.

ALTER TABLE workflow_instance
    ADD COLUMN current_stage      integer NOT NULL DEFAULT -1,
    ADD COLUMN change_request_id  uuid REFERENCES change_request(id) ON DELETE CASCADE,
    ADD COLUMN procedure_id       uuid REFERENCES procedure(id) ON DELETE CASCADE;

-- One task per (stage, approver-role). A stage with several roles opens several tasks in
-- parallel; stages run sequentially. A task is signed (Part 11) when approved.
CREATE TABLE workflow_task (
    id                 uuid PRIMARY KEY,
    tenant_id          uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    instance_id        uuid NOT NULL REFERENCES workflow_instance(id) ON DELETE CASCADE,
    procedure_id       uuid NOT NULL REFERENCES procedure(id) ON DELETE CASCADE,
    change_request_id  uuid NOT NULL REFERENCES change_request(id) ON DELETE CASCADE,
    stage_index        integer NOT NULL,
    stage_name         text NOT NULL,
    assignee_role      text NOT NULL,
    meaning            text NOT NULL CHECK (meaning IN ('AUTHORED','REVIEWED','APPROVED','PUBLISHED','PERIODIC_REVIEWED','RETIRED')),
    status             text NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    decided_by         uuid REFERENCES app_user(id) ON DELETE SET NULL,
    decided_at         timestamptz,
    signature_id       uuid,
    reason             text,
    due_at             timestamptz,
    escalated          boolean NOT NULL DEFAULT false,
    created_at         timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_wftask_queue    ON workflow_task(assignee_role, status);
CREATE INDEX idx_wftask_instance ON workflow_task(instance_id, stage_index);
CREATE INDEX idx_wftask_due      ON workflow_task(status, due_at) WHERE status = 'PENDING';

ALTER TABLE workflow_task ENABLE ROW LEVEL SECURITY;
CREATE POLICY wftask_iso ON workflow_task
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
