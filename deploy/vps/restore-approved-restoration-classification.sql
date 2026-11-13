-- Customer clarification: Restoration is grouped under reconstruction in OMS.
-- Undo only the two canonical project-type changes made during this audit.
SET NAMES utf8mb4;
CREATE TEMPORARY TABLE restoration_assert (ok TINYINT NOT NULL CHECK(ok=1));
START TRANSACTION;
SELECT id FROM projects WHERE site_number IN ('ZP09_03','ZP09_81') AND project_type='subproject' FOR UPDATE;
INSERT INTO restoration_assert SELECT IF(COUNT(*)=2,1,0) FROM projects
WHERE site_number IN ('ZP09_03','ZP09_81') AND project_type='subproject' AND construction_type IN ('restoration','reconstruction');
UPDATE projects SET construction_type='reconstruction'
WHERE site_number IN ('ZP09_03','ZP09_81') AND project_type='subproject' AND construction_type='restoration';
SELECT ROW_COUNT() AS restored_project_types;
-- Keep the raw source caption as provenance, but use the approved OMS category
-- in the value consumed by the currently deployed chart normalizer.
UPDATE project_monitoring_details d JOIN projects p ON p.id=d.project_id
SET d.registry_json=JSON_SET(d.registry_json,'$.constructionType','Reconstruction/ Реконструкція',
 '$.constructionTypeOriginal','Restoration/ Реставрація')
WHERE p.site_number IN ('ZP09_03','ZP09_81') AND p.project_type='subproject';
INSERT INTO restoration_assert SELECT IF(COUNT(*)=2,1,0) FROM projects
WHERE site_number IN ('ZP09_03','ZP09_81') AND project_type='subproject' AND construction_type='reconstruction';
COMMIT;
