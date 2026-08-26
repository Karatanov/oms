-- TiDB-compatible counterpart: use VARCHAR rather than a MySQL ENUM.
ALTER TABLE financial_records
    MODIFY COLUMN payment_purpose VARCHAR(32) NOT NULL DEFAULT 'works';
