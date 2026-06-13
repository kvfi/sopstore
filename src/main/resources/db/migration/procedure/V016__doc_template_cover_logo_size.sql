-- Cover logo size keyword (small | medium | large | xlarge) driving the {{{logo}}} image size.
ALTER TABLE doc_template ADD COLUMN cover_logo_size text NOT NULL DEFAULT 'medium';
