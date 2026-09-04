-- Imported URP III subprojects belong to the sole programme project. Some
-- existing environments contain these rows without parent_project_id, which
-- makes hierarchy-based selectors return an empty list.
UPDATE projects child
JOIN projects root ON root.project_type = 'project'
LEFT JOIN projects other_root
    ON other_root.project_type = 'project'
   AND other_root.id <> root.id
SET child.parent_project_id = root.id
WHERE child.project_type = 'subproject'
  AND child.parent_project_id IS NULL
  AND other_root.id IS NULL;
