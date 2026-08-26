-- Financial charts are presented in EUR. Keep a frozen conversion at save time
-- so historic totals never change when the market rate changes.
ALTER TABLE financial_records
    ADD COLUMN eur_exchange_rate DECIMAL(18,8) NULL AFTER payment_purpose,
    ADD COLUMN eur_exchange_date DATE NULL AFTER eur_exchange_rate,
    ADD COLUMN amount_eur_cents BIGINT NULL AFTER eur_exchange_date;

UPDATE financial_records
SET eur_exchange_rate = 1,
    eur_exchange_date = record_date,
    amount_eur_cents = amount * 100
WHERE currency = 'EUR';

CREATE INDEX idx_financial_eur_date ON financial_records (record_date, amount_eur_cents);
