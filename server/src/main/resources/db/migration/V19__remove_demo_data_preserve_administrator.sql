-- Reset the local MVP database. The administrator account and role catalogue
-- are intentionally retained; every other record is confirmed test data.
DELETE FROM audit_log;
DELETE FROM inspection_photos;
DELETE FROM inspection_report_files;
DELETE FROM inspection_findings;
DELETE FROM inspection_reports;
DELETE FROM financial_records;
DELETE FROM documents;
DELETE FROM incidents;
DELETE FROM projects;
DELETE FROM users WHERE username <> 'admin';
