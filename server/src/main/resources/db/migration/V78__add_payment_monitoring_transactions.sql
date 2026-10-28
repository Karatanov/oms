-- The UR III workbook has dated monthly payment rows in addition to its
-- cumulative SP register. Keep them read-only and separate from OMS documents.
CREATE TABLE payment_monitoring_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_reference VARCHAR(180) NOT NULL,
    source_code VARCHAR(100) NOT NULL,
    batch_id INT NULL,
    payment_date DATE NOT NULL,
    amount_uah DECIMAL(20,2) NOT NULL,
    amount_eur DECIMAL(20,2) NULL,
    source_sheet VARCHAR(180) NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uq_payment_monitoring_transactions_source_reference UNIQUE (source_reference),
    INDEX idx_payment_monitoring_transactions_date (payment_date),
    INDEX idx_payment_monitoring_transactions_code (source_code)
);
