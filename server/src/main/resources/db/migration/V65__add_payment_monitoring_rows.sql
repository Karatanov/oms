CREATE TABLE payment_monitoring_rows (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_code VARCHAR(100) NOT NULL,
    total_investment_uah DECIMAL(20,2) NULL,
    eib_investment_uah DECIMAL(20,2) NULL,
    local_co_financing_uah DECIMAL(20,2) NULL,
    paid_and_in_progress_uah DECIMAL(20,2) NULL,
    paid_uah DECIMAL(20,2) NULL,
    remaining_uah DECIMAL(20,2) NULL,
    planned_eib_share_pct DECIMAL(8,2) NULL,
    factual_eib_share_pct DECIMAL(8,2) NULL,
    eib_share_compliance VARCHAR(255) NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_payment_monitoring_source_code (source_code)
);
