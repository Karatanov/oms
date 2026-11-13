-- Verified against UR III AT&PP - 125 +71 SPs - 20260831 - final.xlsx
-- Sheet: 3. Batch 9 (Allocation), explicit column J, not inferred from names.
-- SHA256: 25d9e2a21f00ae608a0ff7c75339424c1733c16e197a561909dfc590550ed894
SET NAMES utf8mb4;
CREATE TEMPORARY TABLE construction_type_repair (
 code VARCHAR(100) PRIMARY KEY, kind VARCHAR(100) NOT NULL, cell VARCHAR(10) NOT NULL
);
INSERT INTO construction_type_repair VALUES
 ('DP09_13','new_construction','J20'),('ZP09_01','reconstruction','J24'),
 ('ZP09_02','capital_repair','J25'),('ZP09_04','new_construction','J27'),
 ('ZH09_01','capital_repair','J30'),('ZH09_03','reconstruction','J31'),
 ('ZH09_04','reconstruction','J32'),('ZH09_05','reconstruction','J33'),('ZH09_06','reconstruction','J34'),
 ('MY09_01','reconstruction','J51'),('MY09_02','capital_repair','J52'),
 ('MY09_03','new_construction','J53'),('MY09_04','capital_repair','J54'),
 ('OD09_81','new_construction','J69'),('OD09_82','reconstruction','J70'),
 ('PL09_02','reconstruction','J72'),('PL09_05','new_construction','J73'),
 ('PL09_06','reconstruction','J74'),('PL09_09','reconstruction','J75'),('PL09_11','reconstruction','J76'),
 ('PL09_13','capital_repair','J77'),('PL09_14','capital_repair','J78'),
 ('CH09_01','new_construction','J101'),('CH09_02','new_construction','J102'),
 ('CH09_04','reconstruction','J103'),('CH09_06','new_construction','J104');
CREATE TEMPORARY TABLE repair_assert (ok TINYINT NOT NULL CHECK (ok=1));
START TRANSACTION;
SELECT p.id FROM projects p JOIN construction_type_repair r ON r.code=p.site_number
WHERE p.project_type='subproject' FOR UPDATE;
INSERT INTO repair_assert SELECT IF(COUNT(*)=26,1,0)
FROM projects p JOIN construction_type_repair r ON r.code=p.site_number
JOIN project_monitoring_details d ON d.project_id=p.id
WHERE p.project_type='subproject' AND p.status='active' AND JSON_VALID(d.registry_json);
-- Refuse conflicting concurrent edits; never replace an already different type.
INSERT INTO repair_assert SELECT IF(COUNT(*)=0,1,0)
FROM projects p JOIN construction_type_repair r ON r.code=p.site_number
WHERE p.project_type='subproject' AND NULLIF(TRIM(p.construction_type),'') IS NOT NULL AND p.construction_type<>r.kind;
CREATE TEMPORARY TABLE before_construction_projects AS SELECT * FROM projects;
CREATE TEMPORARY TABLE before_construction_details AS SELECT * FROM project_monitoring_details;
UPDATE projects p JOIN construction_type_repair r ON r.code=p.site_number
SET p.construction_type=r.kind WHERE p.project_type='subproject' AND NULLIF(TRIM(p.construction_type),'') IS NULL;
SELECT ROW_COUNT() AS corrected_project_types;
UPDATE project_monitoring_details d JOIN projects p ON p.id=d.project_id
JOIN construction_type_repair r ON r.code=p.site_number
SET d.registry_json=JSON_SET(d.registry_json,
 '$.constructionType', CASE r.kind
 WHEN 'new_construction' THEN 'New construction/ Нове будівництво'
 WHEN 'capital_repair' THEN 'Overhaul/ Капітальний ремонт'
 ELSE 'Reconstruction/ Реконструкція' END,
 '$.constructionTypeSource',CONCAT('UR III AT&PP - 125 +71 SPs - 20260831 - final.xlsx | 3. Batch 9 (Allocation)!',r.cell),
 '$.constructionTypeSourceSha256','25d9e2a21f00ae608a0ff7c75339424c1733c16e197a561909dfc590550ed894')
WHERE p.project_type='subproject';
SELECT ROW_COUNT() AS corrected_source_payloads;
-- Assert that every other project attribute and every other source field is intact.
SET SESSION group_concat_max_len=100000;
SELECT GROUP_CONCAT(CONCAT('NOT(p.',CHAR(96),COLUMN_NAME,CHAR(96),' <=> b.',CHAR(96),COLUMN_NAME,CHAR(96),')') SEPARATOR ' OR ')
INTO @differences FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='projects'
AND COLUMN_NAME NOT IN ('construction_type','updated_at');
SET @query=CONCAT('INSERT INTO repair_assert SELECT IF(COUNT(*)=0,1,0) FROM projects p JOIN before_construction_projects b ON b.id=p.id WHERE ',@differences);
PREPARE comparison FROM @query; EXECUTE comparison; DEALLOCATE PREPARE comparison;
SELECT GROUP_CONCAT(CONCAT('NOT(p.',CHAR(96),COLUMN_NAME,CHAR(96),' <=> b.',CHAR(96),COLUMN_NAME,CHAR(96),')') SEPARATOR ' OR ')
INTO @differences FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='project_monitoring_details'
AND COLUMN_NAME<>'registry_json';
SET @query=CONCAT('INSERT INTO repair_assert SELECT IF(COUNT(*)=0,1,0) FROM project_monitoring_details p JOIN before_construction_details b ON b.project_id=p.project_id WHERE ',@differences);
PREPARE comparison FROM @query; EXECUTE comparison; DEALLOCATE PREPARE comparison;
INSERT INTO repair_assert SELECT IF(COUNT(*)=0,1,0)
FROM project_monitoring_details p JOIN before_construction_details b ON b.project_id=p.project_id
WHERE NOT(JSON_REMOVE(p.registry_json,'$.constructionType','$.constructionTypeSource','$.constructionTypeSourceSha256')
 <=> JSON_REMOVE(b.registry_json,'$.constructionType','$.constructionTypeSource','$.constructionTypeSourceSha256'));
INSERT INTO repair_assert SELECT IF(COUNT(*)=26,1,0) FROM projects p
JOIN construction_type_repair r ON r.code=p.site_number
WHERE p.project_type='subproject' AND p.construction_type=r.kind;
SELECT COUNT(*) AS remaining_missing_construction_types FROM projects
WHERE project_type IN ('subproject','subproject_part') AND NULLIF(TRIM(construction_type),'') IS NULL;
COMMIT;
