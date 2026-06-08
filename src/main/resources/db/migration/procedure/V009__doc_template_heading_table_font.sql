-- Per-template heading and table font sizes (points) for the PDF export. The heading size is a base
-- that drives the document title, section headings, and sub-headings proportionally. Both are
-- clamped to sensible ranges in the application. Defaults reproduce the previous fixed sizes.
ALTER TABLE doc_template ADD COLUMN heading_font_pt double precision NOT NULL DEFAULT 12;
ALTER TABLE doc_template ADD COLUMN table_font_pt double precision NOT NULL DEFAULT 9.5;
