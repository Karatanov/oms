-- TiDB-compatible counterpart of the MySQL migration.
ALTER TABLE financial_records
    ADD COLUMN eur_exchange_rate DECIMAL(18,8) NULL,
    ADD COLUMN eur_exchange_date DATE NULL,
    ADD COLUMN amount_eur_cents BIGINT NULL;

UPDATE financial_records
SET eur_exchange_rate = 1,
    eur_exchange_date = record_date,
    amount_eur_cents = amount * 100
WHERE currency = 'EUR';

CREATE INDEX idx_financial_eur_date ON financial_records (record_date, amount_eur_cents);
