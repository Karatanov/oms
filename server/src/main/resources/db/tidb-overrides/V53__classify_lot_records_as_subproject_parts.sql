-- TiDB-compatible counterpart of V53.  TiDB rejects MySQL's
-- CREATE TEMPORARY TABLE ... AS SELECT, so derive the one representative lot
-- for each missing parent directly in the INSERT query.

INSERT INTO projects (
    uuid, project_type, tranche_number, parent_project_id, name, site_name,
    site_number, address, region, city, latitude, longitude, status, sector,
    construction_type, budget_planned, currency, contractor_name, manager_id,
    created_by
)
SELECT
    UUID(), 'subproject', lot.tranche_number, lot.parent_project_id,
    SUBSTRING_INDEX(lot.site_number, '#', 1),
    SUBSTRING_INDEX(lot.site_number, '#', 1),
    SUBSTRING_INDEX(lot.site_number, '#', 1),
    lot.address, lot.region, lot.city, lot.latitude, lot.longitude, lot.status,
    lot.sector, lot.construction_type, 0, lot.currency, NULL,
    lot.manager_id, lot.created_by
FROM projects lot
INNER JOIN (
    SELECT MIN(id) AS lot_project_id
    FROM projects
    WHERE project_type = 'subproject'
      AND site_number LIKE '%#Lot%'
    GROUP BY SUBSTRING_INDEX(site_number, '#', 1)
) first_lot ON first_lot.lot_project_id = lot.id
WHERE NOT EXISTS (
    SELECT 1
    FROM projects parent
    WHERE parent.project_type = 'subproject'
      AND parent.site_number = SUBSTRING_INDEX(lot.site_number, '#', 1)
);

UPDATE projects part
INNER JOIN projects parent
    ON parent.project_type = 'subproject'
   AND parent.site_number = SUBSTRING_INDEX(part.site_number, '#', 1)
SET part.project_type = 'subproject_part',
    part.parent_project_id = parent.id,
    part.site_name = SUBSTRING_INDEX(part.site_number, '#', -1)
WHERE part.project_type = 'subproject'
  AND part.site_number LIKE '%#Lot%';

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
