-- Author-editable, free-form version label (e.g. "2.0", "1.3-draft"). When set it overrides the
-- computed major.minor label; null falls back to major.minor.
ALTER TABLE procedure_version ADD COLUMN label varchar(64);
