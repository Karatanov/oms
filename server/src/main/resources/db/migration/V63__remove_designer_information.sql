-- Designer information is not present in the approved source database and is
-- deliberately not retained as empty application fields or persistence columns.
ALTER TABLE projects
    DROP COLUMN designer_name,
    DROP COLUMN design_contract_number,
    DROP COLUMN design_contract_term,
    DROP COLUMN design_contract_signing_date,
    DROP COLUMN design_start_date,
    DROP COLUMN design_planned_end_date;
