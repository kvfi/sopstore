-- A procedure's confidentiality classification: a nullable reference to a tenant-managed level.
-- ON DELETE SET NULL so removing a level leaves affected procedures unclassified rather than
-- blocking the delete. The procedure table already enforces RLS (forced in security/V001).
ALTER TABLE procedure
    ADD COLUMN confidentiality_level_id uuid
        REFERENCES confidentiality_level (id) ON DELETE SET NULL;
