-- Align the MVP schema with Section 2.2 of the English supplementary specification.
-- Existing public UUIDs and file-system storage paths are deliberately retained.

ALTER TABLE users
    CHANGE COLUMN password password_hash VARCHAR(255) NOT NULL,
    ADD COLUMN uuid CHAR(36) NULL UNIQUE AFTER id,
    ADD COLUMN first_name VARCHAR(100) NOT NULL DEFAULT '' AFTER id,
    ADD COLUMN last_name VARCHAR(100) NOT NULL DEFAULT '' AFTER first_name,
    ADD COLUMN status ENUM('pending', 'active', 'disabled') NOT NULL DEFAULT 'active' AFTER role_id,
    ADD COLUMN region VARCHAR(100) NULL AFTER status,
    ADD COLUMN department VARCHAR(100) NULL AFTER region,
    ADD COLUMN preferred_lang ENUM('uk', 'en') NOT NULL DEFAULT 'uk' AFTER department,
    ADD COLUMN last_login_at DATETIME NULL AFTER preferred_lang,
    ADD COLUMN failed_login_count TINYINT UNSIGNED NOT NULL DEFAULT 0 AFTER last_login_at,
    ADD COLUMN locked_until DATETIME NULL AFTER failed_login_count,
    ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER locked_until,
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at;

UPDATE users SET uuid = UUID() WHERE uuid IS NULL;

UPDATE users
SET first_name = CASE WHEN username = 'admin' THEN 'System' ELSE first_name END,
    last_name = CASE WHEN username = 'admin' THEN 'Administrator' ELSE last_name END,
    status = 'active'
WHERE username = 'admin';

ALTER TABLE users MODIFY COLUMN uuid CHAR(36) NOT NULL;

ALTER TABLE projects
    ADD COLUMN description TEXT NULL AFTER site_number,
    ADD COLUMN end_date DATE NULL AFTER start_date,
    ADD COLUMN design_contract_signing_date DATE NULL AFTER end_date,
    ADD COLUMN construction_contract_signing_date DATE NULL AFTER design_contract_signing_date,
    ADD COLUMN construction_start_date DATE NULL AFTER construction_contract_signing_date,
    ADD COLUMN projected_completion_time DATE NULL AFTER construction_start_date;

ALTER TABLE inspection_reports
    ADD COLUMN report_code VARCHAR(100) NULL UNIQUE AFTER uuid,
    ADD COLUMN inspection_type ENUM('planned', 'unplanned', 'final') NOT NULL DEFAULT 'planned' AFTER project_id,
    ADD COLUMN reviewed_by BIGINT NULL AFTER submitted_at,
    ADD COLUMN latitude DECIMAL(10, 7) NULL AFTER reviewed_at,
    ADD COLUMN longitude DECIMAL(10, 7) NULL AFTER latitude,
    ADD CONSTRAINT fk_inspection_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id);

ALTER TABLE inspection_photos
    ADD COLUMN caption VARCHAR(500) NULL AFTER content_type,
    ADD COLUMN taken_at DATETIME NULL AFTER caption,
    ADD COLUMN sort_order SMALLINT NOT NULL DEFAULT 0 AFTER taken_at,
    ADD COLUMN uploaded_by BIGINT NULL AFTER sort_order,
    ADD CONSTRAINT fk_photo_uploader FOREIGN KEY (uploaded_by) REFERENCES users(id),
    ADD INDEX idx_photos_sort_order (inspection_report_id, sort_order);

ALTER TABLE financial_records
    ADD COLUMN import_source ENUM('manual', 'xls') NOT NULL DEFAULT 'manual' AFTER milestone;

-- `documents` is the canonical table name from Section 2.2.7. The old name
-- was an implementation detail and is renamed without losing stored metadata.
RENAME TABLE project_documents TO documents;

ALTER TABLE documents
    ADD COLUMN related_entity ENUM('inspection', 'financial_record', 'incident') NULL AFTER project_id,
    ADD COLUMN related_id BIGINT NULL AFTER related_entity,
    ADD COLUMN description TEXT NULL AFTER doc_type,
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
