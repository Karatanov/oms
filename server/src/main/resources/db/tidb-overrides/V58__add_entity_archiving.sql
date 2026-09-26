-- TiDB requires the referenced local columns to exist before adding each FK.
-- Keep this version semantically equivalent to the canonical MySQL migration.
ALTER TABLE users ADD COLUMN is_archived BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN archived_at DATETIME NULL;
ALTER TABLE users ADD COLUMN archived_by BIGINT NULL;
ALTER TABLE users ADD CONSTRAINT fk_users_archived_by FOREIGN KEY (archived_by) REFERENCES users(id) ON DELETE RESTRICT;

ALTER TABLE projects ADD COLUMN is_archived BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE projects ADD COLUMN archived_at DATETIME NULL;
ALTER TABLE projects ADD COLUMN archived_by BIGINT NULL;
ALTER TABLE projects ADD CONSTRAINT fk_projects_archived_by FOREIGN KEY (archived_by) REFERENCES users(id) ON DELETE RESTRICT;

CREATE INDEX idx_users_archived ON users(is_archived);
CREATE INDEX idx_projects_archived ON projects(is_archived);
