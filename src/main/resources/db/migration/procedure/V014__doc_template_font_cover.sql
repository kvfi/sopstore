-- Per-template font selection (optional uploaded body font) and a configurable back cover page.
ALTER TABLE doc_template ADD COLUMN font_family    text    NOT NULL DEFAULT 'IBM Plex Sans';
ALTER TABLE doc_template ADD COLUMN body_font      bytea   NOT NULL DEFAULT '\x';
ALTER TABLE doc_template ADD COLUMN body_font_name text;
ALTER TABLE doc_template ADD COLUMN cover_enabled  boolean NOT NULL DEFAULT false;
ALTER TABLE doc_template ADD COLUMN cover_text     text    NOT NULL DEFAULT '';
ALTER TABLE doc_template ADD COLUMN cover_align    text    NOT NULL DEFAULT 'bottom';
