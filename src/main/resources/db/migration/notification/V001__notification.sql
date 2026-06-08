CREATE TABLE notification_delivery (
    id           uuid PRIMARY KEY,
    tenant_id    uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    recipient_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    template_key text NOT NULL,
    channel      text NOT NULL CHECK (channel IN ('IN_APP','EMAIL','SLACK','TEAMS','WEBHOOK')),
    state        text NOT NULL CHECK (state IN ('QUEUED','SENT','FAILED')),
    queued_at    timestamptz NOT NULL DEFAULT now(),
    delivered_at timestamptz,
    error        text
);

CREATE TABLE email_template (
    id        uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    key       text NOT NULL,
    subject   text NOT NULL,
    body_html text NOT NULL,
    UNIQUE (tenant_id, key)
);

CREATE TABLE notification_preference (
    user_id        uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    category       text NOT NULL,
    channel        text NOT NULL,
    enabled        boolean NOT NULL DEFAULT true,
    quiet_hours    text,
    PRIMARY KEY (user_id, category, channel)
);

ALTER TABLE notification_delivery    ENABLE ROW LEVEL SECURITY;
ALTER TABLE email_template           ENABLE ROW LEVEL SECURITY;

CREATE POLICY nd_iso ON notification_delivery USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY et_iso ON email_template        USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
