-- A procurement belongs to one subproject code; SP ID was an imported duplicate.
-- The former source "Type" and Procurement ID are not OMS business fields.
ALTER TABLE procurement_records DROP COLUMN sp_id;
ALTER TABLE procurement_records DROP COLUMN source_type;
ALTER TABLE procurement_records DROP COLUMN procurement_id;

-- Keep tender links operational even when older imported rows stored just the
-- tender number. The values are the canonical PROZORRO links from the
-- DB_Monitoring source workbook.
UPDATE procurement_records
SET prozorro_tender_id = CASE
    WHEN prozorro_tender_id IS NOT NULL AND TRIM(prozorro_tender_id) <> '' THEN TRIM(prozorro_tender_id)
    WHEN tender_id IS NULL OR TRIM(tender_id) = '' THEN NULL
    WHEN LOWER(TRIM(tender_id)) LIKE 'http%' THEN TRIM(tender_id)
    ELSE CONCAT('https://prozorro.gov.ua/uk/tender/', TRIM(tender_id))
END;

-- Financial records are ledger entries, not documents. Remove the old
-- generated acts and every document row as requested; SIR files and photos
-- are stored in their dedicated tables and are not affected.
DELETE blobs
FROM stored_file_blobs blobs
INNER JOIN documents document ON document.storage_path = blobs.storage_path;
DELETE FROM documents;
