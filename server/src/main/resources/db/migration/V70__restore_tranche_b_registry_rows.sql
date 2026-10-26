-- Some published monitoring exports describe batch 9 as "Batch 9" or
-- "Tranche B".  Older import code treated those text values as tranche A.
-- Correct existing imported rows; future imports normalize these captions in
-- SubprojectRegistryRefreshService.
UPDATE projects AS project
JOIN project_monitoring_details AS monitoring
  ON monitoring.project_id = project.id
SET project.tranche_number = 2,
    monitoring.source_batch_id = 2
WHERE monitoring.source_batch_id IN (2, 9)
   OR LOWER(monitoring.registry_json) LIKE '%"pool":"9"%'
   OR LOWER(monitoring.registry_json) LIKE '%"pool":"batch 9"%'
   OR LOWER(monitoring.registry_json) LIKE '%"pool":"tranche b"%'
   OR LOWER(monitoring.registry_json) LIKE '%"pool":"транш b"%';
