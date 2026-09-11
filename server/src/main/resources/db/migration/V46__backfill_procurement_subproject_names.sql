-- V44 originally staged the source names but omitted them from its final
-- INSERT.  Backfill installations that have already applied that migration.
UPDATE procurement_records pr
JOIN projects p ON p.id = pr.project_id
LEFT JOIN project_monitoring_details md ON md.project_id = p.id
SET pr.subproject_name_uk = COALESCE(NULLIF(pr.subproject_name_uk, ''), p.name),
    pr.subproject_name_en = COALESCE(NULLIF(pr.subproject_name_en, ''), md.name_en)
WHERE pr.subproject_name_uk IS NULL
   OR pr.subproject_name_uk = ''
   OR pr.subproject_name_en IS NULL
   OR pr.subproject_name_en = '';
