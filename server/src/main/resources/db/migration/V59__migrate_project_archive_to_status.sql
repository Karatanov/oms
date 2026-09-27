-- Project lifecycle is represented exclusively by projects.status.
-- Preserve projects archived by the short-lived V58 implementation by mapping
-- them to the existing ARCHIVED status before application code ignores the
-- technical metadata columns.
UPDATE projects
SET status = 'archived'
WHERE is_archived = TRUE;
