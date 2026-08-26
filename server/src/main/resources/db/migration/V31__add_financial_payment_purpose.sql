-- Customer analytics distinguish payments for construction works from equipment purchases.
ALTER TABLE financial_records
    ADD COLUMN payment_purpose ENUM('works', 'equipment') NOT NULL DEFAULT 'works' AFTER milestone;

CREATE INDEX idx_financial_payment_purpose_date
    ON financial_records (payment_purpose, payment_date);
