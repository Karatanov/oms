-- A code containing "#Lot" identifies a part of a subproject, not a
-- standalone subproject. Preserve the existing project row (and therefore
-- all reports, payments, photos and monitoring details attached to it), but
-- insert the missing parent subproject where necessary and move the row into
-- the third hierarchy level.

CREATE TEMPORARY TABLE lot_project_candidates AS
SELECT
    p.id AS lot_project_id,
    SUBSTRING_INDEX(p.site_number, '#', 1) AS parent_site_number
FROM projects p
WHERE p.project_type = 'subproject'
  AND p.site_number LIKE '%#Lot%';

-- Some imported rows only existed at the lot level. Create one lightweight
-- parent subproject per base code without copying the lot budget into it.
INSERT INTO projects (
    uuid, project_type, tranche_number, parent_project_id, name, site_name,
    site_number, address, region, city, latitude, longitude, status, sector,
    construction_type, budget_planned, currency, contractor_name, manager_id,
    created_by
)
SELECT
    UUID(), 'subproject', lot.tranche_number, lot.parent_project_id,
    lot.parent_site_number, lot.parent_site_number, lot.parent_site_number,
    lot.address, lot.region, lot.city, lot.latitude, lot.longitude, lot.status,
    lot.sector, lot.construction_type, 0, lot.currency, NULL,
    lot.manager_id, lot.created_by
FROM projects lot
INNER JOIN (
    SELECT MIN(lot_project_id) AS lot_project_id
    FROM lot_project_candidates
    GROUP BY parent_site_number
) first_lot ON first_lot.lot_project_id = lot.id
WHERE NOT EXISTS (
    SELECT 1
    FROM projects parent
    WHERE parent.project_type = 'subproject'
      AND parent.site_number = SUBSTRING_INDEX(lot.site_number, '#', 1)
);

-- Keep the full code on the part and use the suffix (for example, Lot1) as
-- its concise display name. Existing linked data stays on the same row.
UPDATE projects part
INNER JOIN projects parent
    ON parent.project_type = 'subproject'
   AND parent.site_number = SUBSTRING_INDEX(part.site_number, '#', 1)
SET part.project_type = 'subproject_part',
    part.parent_project_id = parent.id,
    part.site_name = SUBSTRING_INDEX(part.site_number, '#', -1)
WHERE part.project_type = 'subproject'
  AND part.site_number LIKE '%#Lot%';

-- Repair both historical procurement shapes: records that reference the lot
-- project directly and records where the lot code was incorrectly stored as
-- the subproject code. The authoritative relation is now parent subproject
-- plus optional part.
UPDATE procurement_records procurement
INNER JOIN projects part
    ON part.project_type = 'subproject_part'
   AND (
        procurement.project_id = part.id
        OR procurement.sub_project_id = part.site_number
        OR procurement.sub_project_lot_id = part.site_number
   )
INNER JOIN projects parent ON parent.id = part.parent_project_id
SET procurement.project_id = part.id,
    procurement.sub_project_id = parent.site_number,
    procurement.sub_project_lot_id = part.site_number,
    procurement.batch_id = parent.tranche_number,
    procurement.oblast_name = COALESCE(parent.region, procurement.oblast_name),
    procurement.oblast_id = COALESCE(NULLIF(UPPER(SUBSTRING(parent.site_number, 1, 2)), ''), procurement.oblast_id);

DROP TEMPORARY TABLE lot_project_candidates;
