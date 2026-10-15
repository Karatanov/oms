-- DB6.3 column AC links a procurement row to the approved contract document.
-- Historic source exports did not expose the link, so it remains nullable.
ALTER TABLE procurement_records ADD COLUMN contract_link VARCHAR(2048) NULL;
