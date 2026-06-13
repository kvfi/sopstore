-- Optional custom (Mustache) HTML template for the back cover page; blank uses the built-in markup.
ALTER TABLE doc_template ADD COLUMN cover_html text NOT NULL DEFAULT '';
