-- Append-only audit log with hash chain.

CREATE TABLE audit_event (
    id                uuid PRIMARY KEY,
    tenant_id         uuid NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    entity_type       text NOT NULL,
    entity_id         text NOT NULL,
    action            text NOT NULL,
    actor_id          uuid NOT NULL,
    actor_ip          varchar(45),
    actor_user_agent  varchar(512),
    occurred_at       timestamptz NOT NULL DEFAULT now(),
    detail            jsonb NOT NULL DEFAULT '{}'::jsonb,
    prev_hash         varchar(64),
    hash              varchar(64) NOT NULL
);
CREATE INDEX idx_audit_entity     ON audit_event(entity_type, entity_id, occurred_at);
CREATE INDEX idx_audit_tenant_time ON audit_event(tenant_id, occurred_at);

-- Forbid updates and deletes from any role except bypass_rls.
CREATE OR REPLACE FUNCTION audit_event_no_update() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF current_user = 'sopstore_bypass_rls' THEN RETURN NEW; END IF;
    RAISE EXCEPTION 'audit_event is append-only';
END $$;

CREATE TRIGGER trg_audit_no_update
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION audit_event_no_update();

ALTER TABLE audit_event ENABLE ROW LEVEL SECURITY;
CREATE POLICY audit_iso ON audit_event
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
