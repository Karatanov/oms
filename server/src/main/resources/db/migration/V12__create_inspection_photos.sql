CREATE TABLE inspection_photos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid CHAR(36) NOT NULL UNIQUE,
    inspection_report_id BIGINT NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    thumbnail_path VARCHAR(500) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    is_main BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_photo_report FOREIGN KEY (inspection_report_id) REFERENCES inspection_reports(id) ON DELETE CASCADE
);
CREATE INDEX idx_photos_report ON inspection_photos(inspection_report_id);
