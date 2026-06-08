CREATE TABLE webhook_endpoint (
    id           uuid PRIMARY KEY,
    tenant_id    uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    url          text NOT NULL,
    secret       text NOT NULL,
    event_filter text NOT NULL DEFAULT '*',
    active       boolean NOT NULL DEFAULT true,
    created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE webhook_delivery (
    id           uuid PRIMARY KEY,
    tenant_id    uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    endpoint_id  uuid NOT NULL REFERENCES webhook_endpoint(id) ON DELETE CASCADE,
    event_name   text NOT NULL,
    payload      jsonb NOT NULL,
    attempts     int  NOT NULL DEFAULT 0,
    state        text NOT NULL CHECK (state IN ('QUEUED','SENT','FAILED','DLQ')),
    last_status  int,
    last_error   text,
    next_attempt timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_wd_endpoint_state ON webhook_delivery(endpoint_id, state);

ALTER TABLE webhook_endpoint ENABLE ROW LEVEL SECURITY;
ALTER TABLE webhook_delivery ENABLE ROW LEVEL SECURITY;
CREATE POLICY we_iso ON webhook_endpoint USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY wd_iso ON webhook_delivery USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
