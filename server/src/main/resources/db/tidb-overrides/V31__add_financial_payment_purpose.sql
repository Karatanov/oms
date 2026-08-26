-- TiDB-compatible counterpart of the MySQL migration.
ALTER TABLE financial_records
    ADD COLUMN payment_purpose VARCHAR(20) NOT NULL DEFAULT 'works' AFTER milestone;

CREATE INDEX idx_financial_payment_purpose_date
    ON financial_records (payment_purpose, payment_date);
