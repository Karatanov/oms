-- A parent subproject is the public aggregate for its parts.  When its source
-- row has no address, retain a useful address from the first persisted part
-- (the same stable MIN(id) ordering used when V53 created missing parents).
-- Existing parent addresses are intentionally never overwritten.

CREATE TEMPORARY TABLE first_lot_address AS
SELECT part.parent_project_id AS parent_id, part.address
FROM projects part
INNER JOIN (
    SELECT parent_project_id, MIN(id) AS first_part_id
    FROM projects
    WHERE project_type = 'subproject_part'
      AND address IS NOT NULL
      AND TRIM(address) <> ''
    GROUP BY parent_project_id
) first_part ON first_part.first_part_id = part.id;

UPDATE projects subproject
INNER JOIN first_lot_address source ON source.parent_id = subproject.id
SET subproject.address = source.address
WHERE subproject.project_type = 'subproject'
  AND (subproject.address IS NULL OR TRIM(subproject.address) = '');

DROP TEMPORARY TABLE first_lot_address;
