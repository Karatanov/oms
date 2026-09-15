-- Financial records have one business date.  Historic payment dates take
-- precedence because payments from the UR III register were keyed by them.
UPDATE financial_records
SET record_date = payment_date
WHERE payment_date IS NOT NULL;

-- Retain the legacy nullable column for API/database compatibility, but stop
-- storing a second date.  New code always writes NULL here.
UPDATE financial_records
SET payment_date = NULL
WHERE payment_date IS NOT NULL;
