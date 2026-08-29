ALTER TABLE projects
    ADD COLUMN designer_name VARCHAR(255) NULL,
    ADD COLUMN design_contract_number VARCHAR(100) NULL,
    ADD COLUMN design_contract_term VARCHAR(500) NULL,
    ADD COLUMN construction_contract_number VARCHAR(100) NULL;

-- Exact per-field monetary snapshots. Legacy project totals remain whole UAH.
CREATE TABLE project_amounts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    amount_kind VARCHAR(40) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    converted_amount DECIMAL(18,2) NOT NULL,
    uah_per_eur DECIMAL(18,8) NOT NULL,
    rate_date DATE NOT NULL,
    conversion_edited BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE KEY uq_project_amount_kind (project_id, amount_kind),
    CONSTRAINT fk_project_amount_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);
