-- A source lot code such as KH08_07#Lot1 represents a third-level record,
-- not a standalone subproject. The prefix is the subproject code and the
-- suffix (Lot1) is the name of its part. Preserve the original descriptive
-- title in the part description and keep every dependent record on the same
-- project row by converting that row in place.

CREATE TEMPORARY TABLE lot_project_parts AS
SELECT
    id,
    SUBSTRING_INDEX(site_number, '#', 1) AS subproject_code,
    SUBSTRING_INDEX(site_number, '#', -1) AS lot_name,
    name AS original_name,
    tranche_number,
    parent_project_id,
    site_name,
    address,
    region,
    city,
    latitude,
    longitude,
    status,
    sector,
    construction_type,
    budget_planned,
    engineer_consultant_contract_amount,
    technical_supervision_amount,
    subproject_contract_amount,
    currency,
    manager_id,
    created_by
FROM projects
WHERE project_type = 'subproject'
  AND site_number REGEXP '^[A-Z]{2}[0-9]{2}_[0-9]{2}#Lot[0-9]+$';

-- Each lot family gets a single true subproject. Aggregate its financial
-- amounts from the parts, while the original rows retain their own amounts.
INSERT INTO projects (
    uuid, project_type, tranche_number, parent_project_id, name, site_name, site_number,
    description, address, region, city, latitude, longitude, status, sector, construction_type,
    budget_planned, engineer_consultant_contract_amount, technical_supervision_amount,
    subproject_contract_amount, currency, manager_id, created_by
)
SELECT
    UUID(),
    'subproject',
    MIN(source.tranche_number),
    MIN(source.parent_project_id),
    source.subproject_code,
    source.subproject_code,
    source.subproject_code,
    'Automatically grouped from source lot records.',
    MIN(source.address),
    MIN(source.region),
    MIN(source.city),
    MIN(source.latitude),
    MIN(source.longitude),
    MIN(source.status),
    MIN(source.sector),
    MIN(source.construction_type),
    COALESCE(SUM(source.budget_planned), 0),
    SUM(source.engineer_consultant_contract_amount),
    SUM(source.technical_supervision_amount),
    SUM(source.subproject_contract_amount),
    MIN(source.currency),
    MIN(source.manager_id),
    MIN(source.created_by)
FROM lot_project_parts source
LEFT JOIN projects existing
    ON existing.project_type = 'subproject'
   AND existing.site_number = source.subproject_code
WHERE existing.id IS NULL
GROUP BY source.subproject_code;

-- Do not replace the row IDs: procurement, payment, inspection, document and
-- photo foreign keys continue to point to the correct lot after conversion.
UPDATE projects part
INNER JOIN lot_project_parts source ON source.id = part.id
INNER JOIN projects parent
    ON parent.project_type = 'subproject'
   AND parent.site_number = source.subproject_code
SET
    part.description = COALESCE(NULLIF(part.description, ''), source.original_name),
    part.name = source.lot_name,
    part.site_name = source.subproject_code,
    part.project_type = 'subproject_part',
    part.parent_project_id = parent.id;

DROP TEMPORARY TABLE lot_project_parts;
