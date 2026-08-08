-- Imported programme data can contain formal facility names longer than the
-- former 255-character project-name limit. Keep the full official title.
ALTER TABLE projects MODIFY COLUMN name TEXT NOT NULL;
