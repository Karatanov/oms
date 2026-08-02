CREATE TABLE project_documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid CHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL,
    doc_type ENUM('contract','design','estimate','invoice','act','photo','other') NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_document_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_document_creator FOREIGN KEY (created_by) REFERENCES users(id)
);
CREATE INDEX idx_documents_project ON project_documents(project_id);
