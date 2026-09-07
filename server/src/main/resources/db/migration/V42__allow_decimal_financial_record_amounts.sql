-- Financial acts may contain cents. Existing whole-number amounts are
-- preserved exactly while new records retain up to two decimal places.
ALTER TABLE financial_records
    MODIFY COLUMN amount DECIMAL(18,2) NOT NULL;
