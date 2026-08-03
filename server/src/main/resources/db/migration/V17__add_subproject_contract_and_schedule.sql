ALTER TABLE projects
    ADD COLUMN subproject_contract_amount BIGINT NULL AFTER technical_supervision_amount,
    ADD COLUMN start_date DATE NULL AFTER subproject_contract_amount,
    ADD COLUMN contract_signed_date DATE NULL AFTER start_date,
    ADD COLUMN planned_end_date DATE NULL AFTER contract_signed_date;
