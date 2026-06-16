-- Flatten the custom procedure-form: each element is now a field directly (no Section → Fields
-- nesting). Drop the section linkage and the procedure_section table; existing fields survive as a
-- flat, ordered list. Field values still live in each procedure's body JSON, so this never corrupts
-- existing procedure versions.
ALTER TABLE procedure_field DROP COLUMN section_id; -- also drops its FK and the (section_id, …) index
DROP TABLE procedure_section;

CREATE INDEX idx_procedure_field_order ON procedure_field (sort_order);
