-- TiDB-compatible equivalent of the canonical V63 migration.
ALTER TABLE projects DROP COLUMN designer_name;
ALTER TABLE projects DROP COLUMN design_contract_number;
ALTER TABLE projects DROP COLUMN design_contract_term;
ALTER TABLE projects DROP COLUMN design_contract_signing_date;
ALTER TABLE projects DROP COLUMN design_start_date;
ALTER TABLE projects DROP COLUMN design_planned_end_date;
