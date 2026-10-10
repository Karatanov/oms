-- DB6.3 Initial Contracts is the authoritative procurement register.
-- These fields remain nullable because approved rows may be incomplete.
ALTER TABLE procurement_records ADD COLUMN fb_name VARCHAR(500) NULL;
ALTER TABLE procurement_records ADD COLUMN tender_attempt_count INT NULL;
ALTER TABLE procurement_records ADD COLUMN pig_violations TEXT NULL;
ALTER TABLE procurement_records ADD COLUMN actualised_contract_end_date DATE NULL;
ALTER TABLE procurement_records ADD COLUMN contract_amount_uah_without_vat DECIMAL(18,2) NULL;
ALTER TABLE procurement_records ADD COLUMN dream_co_financing_pct DECIMAL(14,10) NULL;
ALTER TABLE procurement_records ADD COLUMN real_local_co_financing_pct DECIMAL(14,10) NULL;
ALTER TABLE procurement_records ADD COLUMN real_eib_financing_uah DECIMAL(18,2) NULL;
ALTER TABLE procurement_records ADD COLUMN bank_guarantee DECIMAL(14,10) NULL;
