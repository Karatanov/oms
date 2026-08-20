-- TiDB variant: unique indexes must be added separately from ADD COLUMN.

ALTER TABLE users
    CHANGE COLUMN password password_hash VARCHAR(255) NOT NULL,
    ADD COLUMN uuid CHAR(36) NULL,
    ADD COLUMN first_name VARCHAR(100) NOT NULL DEFAULT '',
    ADD COLUMN last_name VARCHAR(100) NOT NULL DEFAULT '',
    ADD COLUMN status ENUM('pending', 'active', 'disabled') NOT NULL DEFAULT 'active',
    ADD COLUMN region VARCHAR(100) NULL,
    ADD COLUMN department VARCHAR(100) NULL,
    ADD COLUMN preferred_lang ENUM('uk', 'en') NOT NULL DEFAULT 'uk',
    ADD COLUMN last_login_at DATETIME NULL,
    ADD COLUMN failed_login_count TINYINT UNSIGNED NOT NULL DEFAULT 0,
    ADD COLUMN locked_until DATETIME NULL,
    ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

UPDATE users SET uuid = UUID() WHERE uuid IS NULL;

UPDATE users
SET first_name = CASE WHEN username = 'admin' THEN 'System' ELSE first_name END,
    last_name = CASE WHEN username = 'admin' THEN 'Administrator' ELSE last_name END,
    status = 'active'
WHERE username = 'admin';

ALTER TABLE users MODIFY COLUMN uuid CHAR(36) NOT NULL;
ALTER TABLE users ADD UNIQUE INDEX uk_users_uuid (uuid);

ALTER TABLE projects
    ADD COLUMN description TEXT NULL,
    ADD COLUMN end_date DATE NULL,
    ADD COLUMN design_contract_signing_date DATE NULL,
    ADD COLUMN construction_contract_signing_date DATE NULL,
    ADD COLUMN construction_start_date DATE NULL,
    ADD COLUMN projected_completion_time DATE NULL;

ALTER TABLE inspection_reports
    ADD COLUMN report_code VARCHAR(100) NULL,
    ADD COLUMN inspection_type ENUM('planned', 'unplanned', 'final') NOT NULL DEFAULT 'planned',
    ADD COLUMN reviewed_by BIGINT NULL,
    ADD COLUMN latitude DECIMAL(10, 7) NULL,
    ADD COLUMN longitude DECIMAL(10, 7) NULL;
ALTER TABLE inspection_reports
    ADD CONSTRAINT fk_inspection_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id);
ALTER TABLE inspection_reports ADD UNIQUE INDEX uk_inspection_reports_report_code (report_code);

ALTER TABLE inspection_photos
    ADD COLUMN caption VARCHAR(500) NULL,
    ADD COLUMN taken_at DATETIME NULL,
    ADD COLUMN sort_order SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN uploaded_by BIGINT NULL;
ALTER TABLE inspection_photos
    ADD CONSTRAINT fk_photo_uploader FOREIGN KEY (uploaded_by) REFERENCES users(id),
    ADD INDEX idx_photos_sort_order (inspection_report_id, sort_order);

ALTER TABLE financial_records
    ADD COLUMN import_source ENUM('manual', 'xls') NOT NULL DEFAULT 'manual';

RENAME TABLE project_documents TO documents;

ALTER TABLE documents
    ADD COLUMN related_entity ENUM('inspection', 'financial_record', 'incident') NULL,
    ADD COLUMN related_id BIGINT NULL,
    ADD COLUMN description TEXT NULL,
    ADD INDEX idx_documents_related_entity (related_entity, related_id);

CREATE TABLE incidents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    reported_by BIGINT NOT NULL,
    severity ENUM('low', 'medium', 'high', 'critical') NOT NULL,
    description TEXT NOT NULL,
    incident_date DATETIME NOT NULL,
    latitude DECIMAL(10, 7) NULL,
    longitude DECIMAL(10, 7) NULL,
    status ENUM('open', 'in_progress', 'resolved', 'closed') NOT NULL DEFAULT 'open',
    assigned_to BIGINT NULL,
    resolution_notes TEXT NULL,
    resolved_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_incident_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_incident_reporter FOREIGN KEY (reported_by) REFERENCES users(id),
    CONSTRAINT fk_incident_assignee FOREIGN KEY (assigned_to) REFERENCES users(id),
    INDEX idx_incidents_project_status (project_id, status)
);

CREATE TABLE audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT NOT NULL,
    old_values JSON NULL,
    new_values JSON NULL,
    ip_address VARCHAR(45) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_audit_entity (entity_type, entity_id),
    INDEX idx_audit_created_at (created_at)
);
