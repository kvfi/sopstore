-- Per-template body font size (points) for the PDF export. Default 10pt (slightly tighter than the
-- previous fixed 10.5pt); clamped to a sensible range in the application.
ALTER TABLE doc_template ADD COLUMN body_font_pt double precision NOT NULL DEFAULT 10;
