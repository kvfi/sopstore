-- Attachments: store the original filename on the metadata row, and the file
-- bytes in a separate content table so attachment listings stay lean (content is
-- only read on download / bundle). Any file type is allowed.

ALTER TABLE attachment ADD COLUMN filename text NOT NULL DEFAULT '';

CREATE TABLE attachment_content (
    attachment_id uuid PRIMARY KEY REFERENCES attachment(id) ON DELETE CASCADE,
    tenant_id     uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    content       bytea NOT NULL
);

ALTER TABLE attachment_content ENABLE ROW LEVEL SECURITY;
CREATE POLICY ac_iso ON attachment_content
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
