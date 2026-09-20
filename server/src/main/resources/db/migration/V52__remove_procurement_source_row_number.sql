-- `record_number` was only the ordinal row from an imported workbook. It is
-- neither a procurement business identifier nor user-facing data; the table's
-- primary key remains the internal technical identifier.
ALTER TABLE procurement_records DROP COLUMN record_number;
