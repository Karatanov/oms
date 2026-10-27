-- Rename the visible programme title without changing the stable URP-III technical identifier.
UPDATE projects
SET name = 'Ukraine Recovery III'
WHERE project_type = 'project'
  AND name = 'Ukraine Recovery Programme III';
