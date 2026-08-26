-- Separate financial monitoring of technical-supervision and
-- engineer-consultant payments requires explicit payment purposes.
ALTER TABLE financial_records
    MODIFY COLUMN payment_purpose ENUM('works', 'equipment', 'technical_supervision', 'engineer_consultant') NOT NULL DEFAULT 'works';
