-- Preserve the source-defined dashboard aggregate instead of reconstructing
-- it from component payment rows. The same value is repeated for a snapshot's
-- rows so no additional singleton table is needed.
ALTER TABLE payment_monitoring_rows
    ADD COLUMN dashboard_amount_paid_total_uah DECIMAL(20,2) NULL;
