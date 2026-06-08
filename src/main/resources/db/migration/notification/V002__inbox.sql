-- In-app notification inbox: rendered, persisted messages with per-message read state.
CREATE TABLE notification_inbox (
    id           uuid PRIMARY KEY,
    tenant_id    uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    recipient_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    template_key text NOT NULL,
    title        text NOT NULL,
    body         text NOT NULL,
    link         text,
    created_at   timestamptz NOT NULL DEFAULT now(),
    read_at      timestamptz
);
CREATE INDEX idx_inbox_recipient ON notification_inbox(recipient_id, read_at, created_at DESC);

ALTER TABLE notification_inbox ENABLE ROW LEVEL SECURITY;
CREATE POLICY inbox_iso ON notification_inbox
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
