-- All existing projects and subprojects belong to tranche 1 until their
-- allocation is explicitly managed in a future delivery.
ALTER TABLE projects
    ADD COLUMN tranche_number INT NOT NULL DEFAULT 1 AFTER project_type;
