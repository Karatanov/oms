CREATE TABLE financial_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid CHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL,
    record_type ENUM('invoice', 'act', 'payment', 'advance') NOT NULL,
    reference_number VARCHAR(100) NOT NULL,
    amount BIGINT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'UAH',
    record_date DATE NOT NULL,
    payment_date DATE NULL,
    description TEXT NULL,
    milestone VARCHAR(255) NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_financial_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_financial_creator FOREIGN KEY (created_by) REFERENCES users(id)
);
CREATE INDEX idx_financial_project_date ON financial_records(project_id, record_date);
