-- DB_Monitoring table.xlsx, DB Table!O47:O49: all three lots are Sewerage.
-- Correct the broad Allocation caption in the parent's source metadata only.
SET NAMES utf8mb4;
CREATE TEMPORARY TABLE sector_repair_assert (ok TINYINT NOT NULL CHECK(ok=1));
START TRANSACTION;
SELECT p.id FROM projects p WHERE p.site_number='KH08_02' AND p.project_type='subproject' FOR UPDATE;
INSERT INTO sector_repair_assert SELECT IF(COUNT(*)=1,1,0)
FROM projects p JOIN project_monitoring_details d ON d.project_id=p.id
WHERE p.site_number='KH08_02' AND p.project_type='subproject'
AND p.sector='Водовідведення / Sewerage'
AND d.priority_area_source IN ('Water supply & Sewerage','Водовідведення / Sewerage') AND JSON_VALID(d.registry_json);
INSERT INTO sector_repair_assert SELECT IF(COUNT(*)=3,1,0)
FROM projects lot JOIN projects parent ON parent.id=lot.parent_project_id
JOIN project_monitoring_details d ON d.project_id=lot.id
WHERE parent.site_number='KH08_02' AND lot.project_type='subproject_part'
AND d.priority_area_source='Водовідведення / Sewerage';
SELECT d.registry_json INTO @before_sector_json FROM projects p
JOIN project_monitoring_details d ON d.project_id=p.id WHERE p.site_number='KH08_02' AND p.project_type='subproject';
UPDATE project_monitoring_details d JOIN projects p ON p.id=d.project_id
SET d.priority_area_source='Водовідведення / Sewerage',
 d.registry_json=JSON_SET(d.registry_json,'$.priority','Водовідведення / Sewerage',
 '$.priorityOriginal','Water supply & Sewerage','$.prioritySource','DB_Monitoring table.xlsx | DB Table!O47:O49')
WHERE p.site_number='KH08_02' AND p.project_type='subproject';
SELECT ROW_COUNT() AS corrected_parent_sector_payloads;
INSERT INTO sector_repair_assert SELECT IF(COUNT(*)=1,1,0)
FROM projects p JOIN project_monitoring_details d ON d.project_id=p.id
WHERE p.site_number='KH08_02' AND p.project_type='subproject'
AND d.priority_area_source='Водовідведення / Sewerage'
AND JSON_UNQUOTE(JSON_EXTRACT(d.registry_json,'$.priority'))='Водовідведення / Sewerage'
AND (JSON_REMOVE(d.registry_json,'$.priority','$.priorityOriginal','$.prioritySource')
 <=> JSON_REMOVE(@before_sector_json,'$.priority','$.priorityOriginal','$.prioritySource'));
COMMIT;
