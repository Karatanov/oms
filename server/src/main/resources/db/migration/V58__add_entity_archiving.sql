-- Retain referenced users and project hierarchy records. Existing records stay active.
ALTER TABLE users
    ADD COLUMN is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN archived_at DATETIME NULL,
    ADD COLUMN archived_by BIGINT NULL,
    ADD CONSTRAINT fk_users_archived_by FOREIGN KEY (archived_by) REFERENCES users(id) ON DELETE RESTRICT;

ALTER TABLE projects
    ADD COLUMN is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN archived_at DATETIME NULL,
    ADD COLUMN archived_by BIGINT NULL,
    ADD CONSTRAINT fk_projects_archived_by FOREIGN KEY (archived_by) REFERENCES users(id) ON DELETE RESTRICT;

CREATE INDEX idx_users_archived ON users(is_archived);
CREATE INDEX idx_projects_archived ON projects(is_archived);
