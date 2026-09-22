-- TiDB-compatible counterpart of V56. TiDB does not implement
-- CREATE TEMPORARY TABLE ... AS SELECT, so use the derived source directly.

UPDATE projects subproject
INNER JOIN (
    SELECT part.parent_project_id AS parent_id, part.address
    FROM projects part
    INNER JOIN (
        SELECT parent_project_id, MIN(id) AS first_part_id
        FROM projects
        WHERE project_type = 'subproject_part'
          AND address IS NOT NULL
          AND TRIM(address) <> ''
        GROUP BY parent_project_id
    ) first_part ON first_part.first_part_id = part.id
) source ON source.parent_id = subproject.id
SET subproject.address = source.address
WHERE subproject.project_type = 'subproject'
  AND (subproject.address IS NULL OR TRIM(subproject.address) = '');
