-- Auto-generated procedure document numbers (e.g. SOP-0001).
--
-- One running counter per tenant per document type. The application allocates
-- the next value under a row lock (SELECT … FOR UPDATE) so concurrent creates
-- within a tenant+type get distinct, gap-free numbers. `next_value` holds the
-- number to hand out next; it starts at 1.
CREATE TABLE procedure_number_seq (
    tenant_id  uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    doc_type   text NOT NULL CHECK (doc_type IN ('POLICY','SOP','WORK_INSTRUCTION','FORM','JOB_AID')),
    next_value bigint NOT NULL DEFAULT 1 CHECK (next_value >= 1),
    PRIMARY KEY (tenant_id, doc_type)
);

-- Tenant isolation, consistent with the other procedure-module tables. The
-- security module's final migration additionally FORCEs RLS for the owner role.
ALTER TABLE procedure_number_seq ENABLE ROW LEVEL SECURITY;
CREATE POLICY proc_seq_iso ON procedure_number_seq
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
