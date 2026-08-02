-- Reproducible data for the MVP demonstration. Fixed UUIDs make the API
-- examples in README usable on a clean local MySQL volume.
INSERT INTO projects (
    uuid, project_type, name, site_name, site_number, address, region, city,
    latitude, longitude, status, sector, construction_type, budget_planned,
    currency, contractor_name, manager_id, created_by
)
SELECT
    '11111111-1111-4111-8111-111111111111', 'project',
    'Borodyanka School Reconstruction', 'BORODYANKA', '041',
    '12 Tsentralna Street', 'Kyiv Oblast', 'Borodyanka',
    50.6441000, 29.9122000, 'active', 'Education', 'Reconstruction',
    12500000, 'UAH', 'Demo Construction LLC', 1, 1
WHERE NOT EXISTS (
    SELECT 1 FROM projects WHERE uuid = '11111111-1111-4111-8111-111111111111'
);

INSERT INTO inspection_reports (
    uuid, project_id, inspection_date, completion_pct, summary, created_by, status, submitted_at, reviewed_at
)
SELECT
    '22222222-2222-4222-8222-222222222222', p.id, '2026-08-01', 68.50,
    'Demo inspection: structural work is progressing; one safety finding remains open.',
    1, 'completed', NOW(), NOW()
FROM projects p
WHERE p.uuid = '11111111-1111-4111-8111-111111111111'
  AND NOT EXISTS (
      SELECT 1 FROM inspection_reports WHERE uuid = '22222222-2222-4222-8222-222222222222'
  );

INSERT INTO inspection_findings (
    uuid, inspection_report_id, category, severity, description, recommendation, is_resolved
)
SELECT
    '33333333-3333-4333-8333-333333333333', r.id, 'safety', 'high',
    'Temporary edge protection is incomplete on the second floor.',
    'Install compliant edge protection before the next concrete works.', FALSE
FROM inspection_reports r
WHERE r.uuid = '22222222-2222-4222-8222-222222222222'
  AND NOT EXISTS (
      SELECT 1 FROM inspection_findings WHERE uuid = '33333333-3333-4333-8333-333333333333'
  );

INSERT INTO financial_records (
    uuid, project_id, record_type, reference_number, amount, currency, record_date,
    payment_date, description, milestone, created_by
)
SELECT
    '44444444-4444-4444-8444-444444444444', p.id, 'act', 'ACT-DEMO-001',
    3750000, 'UAH', '2026-07-31', '2026-08-01',
    'Accepted reconstruction works for the first reporting period.', 'Structural works', 1
FROM projects p
WHERE p.uuid = '11111111-1111-4111-8111-111111111111'
  AND NOT EXISTS (
      SELECT 1 FROM financial_records WHERE uuid = '44444444-4444-4444-8444-444444444444'
  );
