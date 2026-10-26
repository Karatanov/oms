-- The tranche-B screening import created valid registry entries as `planned`.
-- The registry deliberately opens with the Active status filter, making every
-- one of those imported entries invisible. Restore only this imported source;
-- manually-created planned subprojects keep their lifecycle status.
UPDATE projects AS project
JOIN project_monitoring_details AS monitoring
  ON monitoring.project_id = project.id
SET project.status = 'active',
    project.is_archived = FALSE,
    project.archived_at = NULL,
    project.archived_by = NULL
WHERE monitoring.source_workbook = 'DB_Screening results.xlsx'
  AND (monitoring.source_batch_id IN (2, 9)
       OR monitoring.source_lot_id LIKE '%09_%');
