-- DB Monitoring Table columns AT/AU define the works contract end and source duration.
-- OMS stores the dates and calculates duration from them, keeping one auditable rule.
UPDATE projects p
JOIN procurement_records pr ON pr.project_id = p.id
SET p.planned_end_date = pr.contract_end_date,
    p.projected_completion_time = pr.contract_end_date
WHERE pr.contract_type = 'works'
  AND pr.contract_end_date IS NOT NULL;
