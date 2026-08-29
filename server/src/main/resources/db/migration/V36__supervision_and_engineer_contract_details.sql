ALTER TABLE projects
    ADD COLUMN technical_supervision_name VARCHAR(255) NULL,
    ADD COLUMN technical_supervision_contract_number VARCHAR(100) NULL,
    ADD COLUMN technical_supervision_contract_date DATE NULL,
    ADD COLUMN technical_supervision_start_date DATE NULL,
    ADD COLUMN technical_supervision_planned_end_date DATE NULL,
    ADD COLUMN engineer_consultant_name VARCHAR(255) NULL,
    ADD COLUMN engineer_consultant_contract_number VARCHAR(100) NULL,
    ADD COLUMN engineer_consultant_contract_date DATE NULL,
    ADD COLUMN engineer_consultant_start_date DATE NULL,
    ADD COLUMN engineer_consultant_planned_end_date DATE NULL;
