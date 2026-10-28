-- DB6 Procurement Tracker fields retained alongside the individual DB6.3
-- contract records. They prevent the Dashboard from counting a procedure's
-- contract total once for every contract amendment or lot.
ALTER TABLE procurement_records ADD COLUMN procurement_id VARCHAR(128) NULL;
ALTER TABLE procurement_records ADD COLUMN signed_contract_amount_uah DECIMAL(18,2) NULL;

CREATE INDEX idx_procurement_records_procurement_id ON procurement_records(procurement_id);
