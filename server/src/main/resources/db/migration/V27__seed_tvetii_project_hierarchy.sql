-- Rebuild the disposable MVP dataset from
-- `TVET II_Energy efficiency indicator status check_07.07.2026.xlsx`.
-- Hierarchy: TVETII project -> coded subproject -> individual facility/part.

SET FOREIGN_KEY_CHECKS = 0;
DELETE FROM audit_log;
DELETE FROM inspection_photos;
DELETE FROM inspection_report_files;
DELETE FROM inspection_findings;
DELETE FROM inspection_reports;
DELETE FROM financial_records;
DELETE FROM documents;
DELETE FROM incidents;
DELETE FROM procurement_records;
DELETE FROM projects;
DELETE FROM users WHERE username <> 'admin';
SET FOREIGN_KEY_CHECKS = 1;

-- Keep the familiar local account and restore the requested demonstration users.
UPDATE users
SET email = 'admin@usif.local', first_name = 'System', last_name = 'Administrator',
    status = 'active', preferred_lang = 'uk'
WHERE username = 'admin';

INSERT INTO users (uuid, username, email, password_hash, role_id, first_name, last_name, status, preferred_lang)
SELECT UUID(), 'gutorovoleg', 'gutorovoleg@gmail.com',
       '$2a$10$zS2RIdZCHxLWdi548DU.R.qhMlkPwsyR1qfLUwuvVwY4twlt0s.HK',
       r.id, 'Олег', 'Гуторов', 'active', 'uk'
FROM roles r WHERE r.code = 'ADMIN';

INSERT INTO users (uuid, username, email, password_hash, role_id, first_name, last_name, status, preferred_lang)
SELECT UUID(), 'pne', 'pne@post.com',
       '$2a$10$zS2RIdZCHxLWdi548DU.R.qhMlkPwsyR1qfLUwuvVwY4twlt0s.HK',
       r.id, 'Pawel', 'Neugebauer', 'active', 'en'
FROM roles r WHERE r.code = 'PROJECT_MANAGER';

INSERT INTO users (uuid, username, email, password_hash, role_id, first_name, last_name, status, preferred_lang)
SELECT UUID(), 'skaratanov', 'skaratanov@gmail.com',
       '$2a$10$zS2RIdZCHxLWdi548DU.R.qhMlkPwsyR1qfLUwuvVwY4twlt0s.HK',
       r.id, 'Сергій', 'Каратанов', 'active', 'uk'
FROM roles r WHERE r.code = 'INSPECTOR';

INSERT INTO projects (
    uuid, project_type, name, site_name, site_number, description, address, region, city,
    latitude, longitude, status, sector, construction_type, budget_planned, start_date, contract_signed_date, planned_end_date, currency,
    manager_id, created_by
)
SELECT
    'b0000000-0000-4000-8000-000000000001', 'project',
    'Vocational Education and Training in the Eastern Partnership II', 'TVETII', 'TVETII',
    'Umbrella project for the TVET II energy-efficiency facilities listed in the indicator status check of 07.07.2026.',
    'Ukraine', 'Ukraine', 'Kyiv', 49.0000000, 31.0000000,
    'active', 'Education', 'reconstruction', 1, '2026-01-01', '2025-12-01', '2026-12-31', 'UAH',
    (SELECT id FROM users WHERE username = 'pne'),
    (SELECT id FROM users WHERE username = 'admin');

INSERT INTO projects (
    uuid, project_type, parent_project_id, name, site_name, site_number, description, address, region, city,
    latitude, longitude, status, sector, construction_type, budget_planned, subproject_contract_amount, start_date, contract_signed_date, planned_end_date, currency, manager_id, created_by
)
SELECT
    'b0000000-0000-4000-8000-000000000073', 'subproject', root.id,
    '34-73-2', 'TVETII', '34-73-2', 'TVET II subproject code 34-73-2.',
    'Khotynska Street, 47', 'Chernivtsi Oblast', 'Chernivtsi', 48.2915000, 25.9403000,
    'active', 'Education', 'reconstruction', 1, 1, '2026-01-01', '2025-12-01', '2026-12-31', 'UAH',
    (SELECT id FROM users WHERE username = 'pne'), (SELECT id FROM users WHERE username = 'admin')
FROM projects root WHERE root.uuid = 'b0000000-0000-4000-8000-000000000001';

INSERT INTO projects (
    uuid, project_type, parent_project_id, name, site_name, site_number, description, address, region, city,
    latitude, longitude, status, sector, construction_type, budget_planned, subproject_contract_amount, start_date, contract_signed_date, planned_end_date, currency, manager_id, created_by
)
SELECT
    'b0000000-0000-4000-8000-000000000046', 'subproject', root.id,
    '34-46-1', 'TVETII', '34-46-1', 'TVET II subproject code 34-46-1.',
    'Ivanny Blazhkevych Street', 'Lviv Oblast', 'Lviv', 49.8397000, 24.0297000,
    'active', 'Education', 'reconstruction', 1, 1, '2026-01-01', '2025-12-01', '2026-12-31', 'UAH',
    (SELECT id FROM users WHERE username = 'pne'), (SELECT id FROM users WHERE username = 'admin')
FROM projects root WHERE root.uuid = 'b0000000-0000-4000-8000-000000000001';

INSERT INTO projects (
    uuid, project_type, parent_project_id, name, site_name, site_number, description, address, region, city,
    latitude, longitude, status, sector, construction_type, budget_planned, subproject_contract_amount, start_date, contract_signed_date, planned_end_date, currency, manager_id, created_by
)
SELECT part_uuid, 'subproject_part', parent.id, part_name, parent.site_number, CONCAT(parent.site_number, '-', part_number),
       part_name, part_address, parent.region, parent.city, parent.latitude, parent.longitude,
       'active', 'Education', 'reconstruction', 1, 1, '2026-01-01', '2025-12-01', '2026-12-31', 'UAH',
       (SELECT id FROM users WHERE username = 'pne'), (SELECT id FROM users WHERE username = 'admin')
FROM projects parent
JOIN (
    SELECT 'b0000000-0000-4000-8000-000000000731' AS part_uuid, '34-73-2' AS subproject_code, '01' AS part_number,
           'Dormitory of the educational institution "State Vocational and Technical Educational Institution" "Chernivtsi Professional Machine-Building Lyceum" at the address: Chernivtsi region, Chernivtsi city, Khotynska street, 47 G' AS part_name,
           'Khotynska Street, 47 G, Chernivtsi' AS part_address
    UNION ALL SELECT 'b0000000-0000-4000-8000-000000000732', '34-73-2', '02',
           'Car repair shop of the educational institution "State Vocational and Technical Educational Institution" "Chernivtsi Professional Machine-Building Lyceum" at the address: Chernivtsi region, Chernivtsi city, Khotynska street, 47 G',
           'Khotynska Street, 47 G, Chernivtsi'
    UNION ALL SELECT 'b0000000-0000-4000-8000-000000000733', '34-73-2', '03',
           'Educational building (workshop, building B) of the educational institution "State Vocational and Technical Educational Institution" "Chernivtsi Professional Machine-Building Lyceum" at the address: Chernivtsi region, Chernivtsi city, Khotynska street, 47 G',
           'Khotynska Street, 47 G, Chernivtsi'
    UNION ALL SELECT 'b0000000-0000-4000-8000-000000000734', '34-73-2', '04',
           'Educational building of the educational institution "State Vocational and Technical Educational Institution" "Chernivtsi Professional Machine-Building Lyceum" at the address: Chernivtsi region, Chernivtsi city, Khotynska st., 47 D',
           'Khotynska Street, 47 D, Chernivtsi'
    UNION ALL SELECT 'b0000000-0000-4000-8000-000000000461', '34-46-1', '01',
           'Dormiory of the educational institution "Lviv Interregional Higher Vocational School of Railway Transport" at the address: 10a Ivanny Blazhkevych St., Lviv, Lviv region.',
           '10a Ivanny Blazhkevych Street, Lviv'
    UNION ALL SELECT 'b0000000-0000-4000-8000-000000000462', '34-46-1', '02',
           'Workshop (letter A3-1) of the Lviv Interregional Higher Vocational School of Railway Transport at the address: 14 Ivanny Blazhkevych St., Lviv, Lviv region.',
           '14 Ivanny Blazhkevych Street, Lviv'
    UNION ALL SELECT 'b0000000-0000-4000-8000-000000000463', '34-46-1', '03',
           'Educational building lit.A-4,A1-2,A2-4 of the Lviv Interregional Higher Professional School of Railway Transport at the address: 14 Ivanny Blazhkevych St., Lviv, Lviv region.',
           '14 Ivanny Blazhkevych Street, Lviv'
) source_data ON source_data.subproject_code = parent.site_number
WHERE parent.project_type = 'subproject';
