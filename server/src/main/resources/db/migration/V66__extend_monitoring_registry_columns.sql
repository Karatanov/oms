-- Source-aligned fields for the wide Subprojects monitoring registry.
-- Nullable by design: an approved source workbook may legitimately omit a
-- particular E-construction registration or construction progress value.
ALTER TABLE project_monitoring_details
    ADD COLUMN contractor_name_en TEXT NULL,
    ADD COLUMN ded_url VARCHAR(1000) NULL,
    ADD COLUMN estimate_url VARCHAR(1000) NULL,
    ADD COLUMN expertise_url VARCHAR(1000) NULL,
    ADD COLUMN permit_url VARCHAR(1000) NULL,
    ADD COLUMN construction_act_number VARCHAR(255) NULL,
    ADD COLUMN construction_act_date VARCHAR(64) NULL,
    ADD COLUMN completed_work_pct VARCHAR(64) NULL,
    ADD COLUMN financing_used_pct VARCHAR(64) NULL,
    ADD COLUMN paid_financing_pct VARCHAR(64) NULL,
    ADD COLUMN construction_work_summary TEXT NULL,
    ADD COLUMN construction_work_comments TEXT NULL,
    ADD COLUMN registry_json LONGTEXT NULL;
