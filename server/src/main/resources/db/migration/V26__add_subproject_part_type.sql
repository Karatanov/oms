ALTER TABLE projects
    MODIFY COLUMN project_type ENUM('project', 'subproject', 'subproject_part') NOT NULL DEFAULT 'project';
