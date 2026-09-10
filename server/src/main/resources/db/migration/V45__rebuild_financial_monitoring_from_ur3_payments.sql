-- Rebuild financial monitoring from UR III Payments.xlsx.
-- Only rows with a concrete payment date (including “Sent for payment <date>”) are financial payments.
-- “Planned” rows have no payment date and are deliberately not converted into actual payments.

CREATE TEMPORARY TABLE payment_import_stage (
    reference_number VARCHAR(100) NOT NULL,
    batch_id INT NOT NULL,
    source_payment_id VARCHAR(100) NOT NULL,
    project_key VARCHAR(100) NOT NULL,
    payment_date DATE NOT NULL,
    amount_uah DECIMAL(18,2) NOT NULL,
    amount_eur DECIMAL(18,4) NOT NULL,
    payment_purpose VARCHAR(32) NOT NULL,
    source_sheet VARCHAR(255) NOT NULL,
    source_status VARCHAR(100) NULL
);

INSERT INTO payment_import_stage (
    reference_number, batch_id, source_payment_id, project_key, payment_date,
    amount_uah, amount_eur, payment_purpose, source_sheet, source_status
) VALUES
('URIII-PMT-20260902-001', 8, 'KM08_02', 'KM08_02', '2026-09-02', 2803440.0, 56635.15, 'works', 'UR III SEPTEMBER 2026 payments', NULL),
('URIII-PMT-20260901-002', 8, 'ZH08_04#Lot2 TS', 'ZH08_04#Lot2', '2026-09-01', 49245.95, 994.87, 'technical_supervision', 'UR III SEPTEMBER 2026 payments', 'Sent for payment 01.09.2026'),
('URIII-PMT-20260901-003', 8, 'ZH08_04#Lot2 W', 'ZH08_04#Lot2', '2026-09-01', 3649051.99, 73718.22, 'works', 'UR III SEPTEMBER 2026 payments', 'Sent for payment 01.09.2026'),
('URIII-PMT-20260827-004', 8, 'LV08_01', 'LV08_01', '2026-08-27', 7955436.57, 160715.89, 'works', 'UR III SEPTEMBER 2026 payments', 'Sent for payment 27.08.2026'),
('URIII-PMT-20260902-005', 8, 'ZK08_07', 'ZK08_07', '2026-09-02', 1977169.06, 39942.81, 'works', 'UR III SEPTEMBER 2026 payments', NULL),
('URIII-PMT-20260903-006', 8, 'ZP08_05', 'ZP08_05', '2026-09-03', 944209.67, 19074.94, 'works', 'UR III SEPTEMBER 2026 payments', NULL),
('URIII-PMT-20260903-007', 8, 'KH08_11#Lot1', 'KH08_11#Lot1', '2026-09-03', 4495196.54, 90812.05, 'works', 'UR III SEPTEMBER 2026 payments', 'Sent for payment 03.09.2026'),
('URIII-PMT-20260903-008', 8, 'KH08_11#Lot2', 'KH08_11#Lot2', '2026-09-03', 4979804.86, 100602.12, 'works', 'UR III SEPTEMBER 2026 payments', 'Sent for payment 03.09.2026'),
('URIII-PMT-20260811-009', 8, 'ZP08_05 W', 'ZP08_05', '2026-08-11', 2283083.25, 46122.89, 'works', 'UR III AUGUST 2026 payments', NULL),
('URIII-PMT-20260812-010', 8, 'ZK08_07 W', 'ZK08_07', '2026-08-12', 809840.26, 16360.41, 'works', 'UR III AUGUST 2026 payments', NULL),
('URIII-PMT-20260813-011', 8, 'ZP08_03', 'ZP08_03', '2026-08-13', 4825693.87, 97488.77, 'works', 'UR III AUGUST 2026 payments', NULL),
('URIII-PMT-20260821-012', 8, 'OD08_03 W', 'OD08_03', '2026-08-21', 1974706.52, 39893.06, 'works', 'UR III AUGUST 2026 payments', NULL),
('URIII-PMT-20260821-013', 8, 'ZP08_05', 'ZP08_05', '2026-08-21', 39975000.0, 807575.76, 'works', 'UR III AUGUST 2026 payments', NULL),
('URIII-PMT-20260826-014', 8, 'ZK08_07 W', 'ZK08_07', '2026-08-26', 673111.94, 13598.22, 'works', 'UR III AUGUST 2026 payments', NULL),
('URIII-PMT-20260827-015', 8, 'CK08_05 W', 'CK08_05', '2026-08-27', 2402694.99, 48539.29, 'works', 'UR III AUGUST 2026 payments', NULL),
('URIII-PMT-20260703-016', 8, 'CK08_05 W', 'CK08_05', '2026-07-03', 921722.86, 18620.66, 'works', 'UR III JULY 2026 payments', NULL),
('URIII-PMT-20260703-017', 8, 'CK08_05 TS', 'CK08_05', '2026-07-03', 9534.13, 192.61, 'technical_supervision', 'UR III JULY 2026 payments', NULL),
('URIII-PMT-20260716-018', 8, 'KM08_02 W', 'KM08_02', '2026-07-16', 2196977.0, 44383.37, 'works', 'UR III JULY 2026 payments', NULL),
('URIII-PMT-20260716-019', 8, 'DP08_06 TS', 'DP08_06', '2026-07-16', 617461.47, 12473.97, 'technical_supervision', 'UR III JULY 2026 payments', NULL),
('URIII-PMT-20260720-020', 8, 'CK08_05 W', 'CK08_05', '2026-07-20', 1699878.14, 34340.97, 'works', 'UR III JULY 2026 payments', NULL),
('URIII-PMT-20260720-021', 8, 'CK08_05 TS', 'CK08_05', '2026-07-20', 17848.72, 360.58, 'technical_supervision', 'UR III JULY 2026 payments', NULL),
('URIII-PMT-20260717-022', 8, 'RV08_04', 'RV08_04', '2026-07-17', 49433.7, 998.661, 'works', 'UR III JULY 2026 payments', NULL),
('URIII-PMT-20260716-023', 8, 'ZP08_03', 'ZP08_03', '2026-07-16', 3945145.95, 79699.92, 'works', 'UR III JULY 2026 payments', NULL),
('URIII-PMT-20260717-024', 8, 'KH08_07 Lot1', 'KH08_07#Lot1', '2026-07-17', 85873.87, 1734.83, 'works', 'UR III JULY 2026 payments', NULL),
('URIII-PMT-20260717-025', 8, 'KH08_07 Lot2', 'KH08_07#Lot2', '2026-07-17', 148163.19, 2993.2, 'works', 'UR III JULY 2026 payments', NULL),
('URIII-PMT-20260717-026', 8, 'KH08_07 Lot3', 'KH08_07#Lot3', '2026-07-17', 142939.58, 2887.67, 'works', 'UR III JULY 2026 payments', NULL),
('URIII-PMT-20260717-027', 8, 'KH08_07 Lot6', 'KH08_07#Lot6', '2026-07-17', 59384.29, 1199.68, 'works', 'UR III JULY 2026 payments', NULL),
('URIII-PMT-20260601-028', 8, 'KV08_08 W', 'KV08_08', '2026-06-01', 544993.95, 11009.98, 'works', 'UR III JUNE 2026 payments', NULL),
('URIII-PMT-20260601-029', 8, 'KV08_07 W', 'KV08_07', '2026-06-01', 523153.82, 10568.77, 'works', 'UR III JUNE 2026 payments', NULL),
('URIII-PMT-20260602-030', 8, 'ZH08_04#Lot3 W', 'ZH08_04#Lot3', '2026-06-02', 906336.46, 18309.83, 'works', 'UR III JUNE 2026 payments', NULL),
('URIII-PMT-20260602-031', 8, 'ZH08_04#Lot3 TS', 'ZH08_04#Lot3', '2026-06-02', 13032.34, 263.28, 'technical_supervision', 'UR III JUNE 2026 payments', NULL),
('URIII-PMT-20260603-032', 8, 'ZH08_04#Lot4 W', 'ZH08_04#Lot4', '2026-06-03', 947664.87, 19144.74, 'works', 'UR III JUNE 2026 payments', NULL),
('URIII-PMT-20260603-033', 8, 'ZH08_04#Lot4 TS', 'ZH08_04#Lot4', '2026-06-03', 13651.67, 275.79, 'technical_supervision', 'UR III JUNE 2026 payments', NULL),
('URIII-PMT-20260608-034', 8, 'KH08_10 W', 'KH08_10', '2026-06-08', 5012700.0, 101266.67, 'works', 'UR III JUNE 2026 payments', NULL),
('URIII-PMT-20260622-035', 8, 'KM08_02 W', 'KM08_02', '2026-06-22', 869198.0, 17559.56, 'works', 'UR III JUNE 2026 payments', NULL),
('URIII-PMT-20260616-036', 8, 'CK08_05 W', 'CK08_05', '2026-06-16', 925296.84, 18692.87, 'works', 'UR III JUNE 2026 payments', NULL),
('URIII-PMT-20260616-037', 8, 'CK08_05 TS', 'CK08_05', '2026-06-16', 9536.81, 192.66, 'technical_supervision', 'UR III JUNE 2026 payments', NULL),
('URIII-PMT-20260512-038', 8, 'RV08_04(Lot1)', 'RV08_04#Lot1', '2026-05-12', 4000000.0, 80808.08, 'works', 'UR III MAY 2026 payments', NULL),
('URIII-PMT-20260514-039', 8, 'ZP08_03 (W)', 'ZP08_03', '2026-05-14', 2658554.04, 53708.16, 'works', 'UR III MAY 2026 payments', '5/14/2026'),
('URIII-PMT-20260518-040', 8, 'CK08_05 (W)', 'CK08_05', '2026-05-18', 97777.7, 1975.31, 'works', 'UR III MAY 2026 payments', NULL),
('URIII-PMT-20260518-041', 8, 'CK08_05 (TS)', 'CK08_05', '2026-05-18', 8915.0, 180.1, 'technical_supervision', 'UR III MAY 2026 payments', NULL),
('URIII-PMT-20260518-042', 8, 'KH08_09#Lot1 (W)', 'KH08_09#Lot1', '2026-05-18', 2831540.0, 57202.83, 'works', 'UR III MAY 2026 payments', NULL),
('URIII-PMT-20260518-043', 8, 'KH08_09#Lot2 (W)', 'KH08_09#Lot2', '2026-05-18', 4372310.0, 88329.49, 'works', 'UR III MAY 2026 payments', NULL),
('URIII-PMT-20260520-044', 8, 'ZP08_04 (TS)', 'ZP08_04', '2026-05-20', 10189.46, 205.85, 'technical_supervision', 'UR III MAY 2026 payments', NULL),
('URIII-PMT-20260528-045', 8, 'KM08_02 (W)', 'KM08_02', '2026-05-28', 1130385.0, 22836.06, 'works', 'UR III MAY 2026 payments', NULL),
('URIII-PMT-20260421-046', 8, 'KV08_07', 'KV08_07', '2026-04-21', 671723.9, 13570.18, 'works', 'UR III APRIL 2026 payments', NULL),
('URIII-PMT-20260421-047', 8, 'KV08_08', 'KV08_08', '2026-04-21', 819641.25, 16558.41, 'works', 'UR III APRIL 2026 payments', NULL),
('URIII-PMT-20260414-048', 8, 'ZP08_04', 'ZP08_04', '2026-04-14', 377224.28, 7620.69, 'works', 'UR III APRIL 2026 payments', '4/14/2026'),
('URIII-PMT-20260424-049', 8, 'CK08_06', 'CK08_06', '2026-04-24', 51290.0, 497.0, 'works', 'UR III APRIL 2026 payments', '4/24/2026'),
('URIII-PMT-20260424-050', 8, 'RV08_04(Lot2)', 'RV08_04#Lot2', '2026-04-24', 3529443.71, 71301.89, 'works', 'UR III APRIL 2026 payments', '4/24/2026'),
('URIII-PMT-20260319-051', 8, 'CV08_03', 'CV08_03', '2026-03-19', 3027826.54, 61168.21, 'works', 'UR III MARCH 2026 payments', NULL),
('URIII-PMT-20260316-052', 8, 'DP08_06', 'DP08_06', '2026-03-16', 48500000.0, 979798.0, 'works', 'UR III MARCH 2026 payments', NULL),
('URIII-PMT-20260217-053', 8, 'ZP08_03_W', 'ZP08_03', '2026-02-17', 31596259.84, 644821.63, 'works', 'UR III FEBRUARY 2026 payments', NULL),
('URIII-PMT-20260217-054', 8, 'ZP08_03_W', 'ZP08_03', '2026-02-17', 1581692.67, 32279.44, 'works', 'UR III FEBRUARY 2026 payments', NULL),
('URIII-PMT-20251230-055', 8, 'CK08_06_W', 'CK08_06', '2025-12-30', 749408.95, 15612.69, 'works', 'UR III DECEMBER 2025 payments', NULL),
('URIII-PMT-20251224-056', 8, 'CV08_03_W', 'CV08_03', '2025-12-24', 999269.41, 21037.25, 'works', 'UR III DECEMBER 2025 payments', NULL),
('URIII-PMT-20251225-057', 8, 'ZP08_03_W', 'ZP08_03', '2025-12-25', 36300365.83, 764218.228, 'works', 'UR III DECEMBER 2025 payments', NULL),
('URIII-PMT-20251225-058', 8, 'ZP08_04_W', 'ZP08_04', '2025-12-25', 1998311.35, 41631.49, 'works', 'UR III DECEMBER 2025 payments', NULL);

-- Documents are reset together with financial monitoring. Remove just their
-- durable mirrors, preserving SIR and photo files stored in the same blob table.
DELETE blobs
FROM stored_file_blobs blobs
INNER JOIN documents document ON document.storage_path = blobs.storage_path;
DELETE FROM documents;
DELETE FROM financial_records;

-- For source IDs which do not yet have a matching project/subproject record,
-- add a minimal subproject under the UR III programme. Exact project keys are
-- used first; code-level records created by the procurement import remain valid parents.
INSERT INTO projects (
    uuid, project_type, tranche_number, parent_project_id, name, site_name, site_number,
    description, address, region, city, latitude, longitude, status, sector, construction_type,
    budget_planned, currency, created_by
)
SELECT UUID(), 'subproject', s.batch_id,
    (SELECT id FROM projects WHERE uuid = '42611a16-f360-5f8b-87a8-00680318888b' LIMIT 1),
    s.project_key, s.project_key, s.project_key,
    NULL, NULL, NULL, NULL, NULL, NULL, 'planned', NULL, NULL, 0, 'UAH',
    (SELECT id FROM users WHERE username = 'admin' LIMIT 1)
FROM payment_import_stage s
WHERE NOT EXISTS (SELECT 1 FROM projects p WHERE p.site_number = s.project_key);

INSERT INTO financial_records (
    uuid, project_id, record_type, reference_number, amount, currency, record_date,
    payment_date, description, milestone, payment_purpose, eur_exchange_rate,
    eur_exchange_date, amount_eur_cents, created_by, import_source
)
SELECT
    UUID(),
    COALESCE(
        (SELECT p.id FROM projects p WHERE p.site_number = s.project_key LIMIT 1),
        (SELECT p.id FROM projects p WHERE p.site_number = SUBSTRING_INDEX(s.project_key, '#', 1) LIMIT 1)
    ),
    'payment', s.reference_number, s.amount_uah, 'UAH', s.payment_date, s.payment_date,
    CONCAT('UR III Payments.xlsx — ', s.source_sheet,
           '; source ID: ', s.source_payment_id,
           IF(s.source_status IS NULL, '', CONCAT('; ', s.source_status))),
    s.source_payment_id, s.payment_purpose,
    ROUND(s.amount_uah / NULLIF(s.amount_eur, 0), 8), s.payment_date,
    ROUND(s.amount_eur * 100), (SELECT id FROM users WHERE username = 'admin' LIMIT 1), 'xls'
FROM payment_import_stage s;

DROP TEMPORARY TABLE payment_import_stage;
