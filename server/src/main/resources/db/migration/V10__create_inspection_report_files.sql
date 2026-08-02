CREATE TABLE inspection_report_files (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inspection_report_id BIGINT NOT NULL UNIQUE,
    original_name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_report_file_report FOREIGN KEY (inspection_report_id)
        REFERENCES inspection_reports(id) ON DELETE CASCADE
);
