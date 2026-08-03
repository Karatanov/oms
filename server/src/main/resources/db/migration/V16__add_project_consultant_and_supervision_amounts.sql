ALTER TABLE projects
    ADD COLUMN engineer_consultant_contract_amount BIGINT NULL AFTER budget_planned,
    ADD COLUMN technical_supervision_amount BIGINT NULL AFTER engineer_consultant_contract_amount;
