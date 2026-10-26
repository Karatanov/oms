-- Repair the visible registry independently of the original screening workbook
-- metadata.  Batch 9 Allocation (2026-08-31) is the approved source and
-- contains exactly these 71 subprojects.  Older imports may have persisted a
-- different `source_workbook` value, so V71's source-specific update could
-- leave valid Batch 9 rows in the non-default `planned` status.
UPDATE projects
SET status = 'active',
    is_archived = FALSE,
    archived_at = NULL,
    archived_by = NULL
WHERE project_type = 'subproject'
  AND UPPER(site_number) IN (
    'VN09_01', 'VN09_02', 'VN09_05', 'VO09_01', 'DP09_04', 'DP09_05',
    'DP09_12', 'DP09_13', 'ZP09_01', 'ZP09_02', 'ZP09_03', 'ZP09_04',
    'ZP09_81', 'ZH09_01', 'ZH09_03', 'ZH09_04', 'ZH09_05', 'ZH09_06',
    'KV09_04', 'KV09_06', 'KV09_07', 'KV09_08', 'KV09_09', 'KV09_10',
    'KV09_11', 'KR09_03', 'KR09_04', 'KR09_05', 'MY09_01', 'MY09_02',
    'MY09_03', 'MY09_04', 'MY09_05', 'MY09_06', 'MY09_08', 'MY09_09',
    'MY09_11', 'MY09_12', 'MY09_14', 'OD09_02', 'OD09_04', 'OD09_06',
    'OD09_08', 'OD09_09', 'OD09_11', 'OD09_81', 'OD09_82', 'PL09_02',
    'PL09_05', 'PL09_06', 'PL09_09', 'PL09_11', 'PL09_13', 'PL09_14',
    'PL09_81', 'SM09_02', 'SM09_04', 'SM09_06', 'KH09_04', 'KH09_05',
    'KH09_07', 'KM09_81', 'KM09_82', 'CK09_03', 'CK09_06', 'CK09_81',
    'CK09_82', 'CH09_01', 'CH09_02', 'CH09_04', 'CH09_06'
  );
