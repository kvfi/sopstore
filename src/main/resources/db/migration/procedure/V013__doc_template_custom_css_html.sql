-- Per-template overrides for the PDF export. Both are optional text blobs:
--   custom_css  appended after the built-in stylesheet (override mode), so a template can restyle
--               the document without replacing the defaults.
--   custom_html a full page template (Mustache: {{title}}, {{#steps}}…, {{{defaultBody}}}, …). When
--               present it renders the whole page; a malformed one falls back to the built-in layout
--               at export time, so it can never break PDF generation.
ALTER TABLE doc_template ADD COLUMN custom_css  text NOT NULL DEFAULT '';
ALTER TABLE doc_template ADD COLUMN custom_html text NOT NULL DEFAULT '';
