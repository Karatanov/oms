-- DB6.3 uses the tender-row suffix as the authoritative contract Type.
-- Correct historic cached Type values before dashboard aggregates run.
UPDATE procurement_records
SET source_contract_type = CASE UPPER(SUBSTRING_INDEX(SUBSTRING_INDEX(tender_id, '$', 1), '_', -1))
    WHEN 'W' THEN 'Роботи / Works'
    WHEN 'TS' THEN 'Технічний нагляд / TS'
    WHEN 'CSC' THEN 'Інженер-консультант / CSC'
    ELSE source_contract_type
END
WHERE UPPER(SUBSTRING_INDEX(SUBSTRING_INDEX(tender_id, '$', 1), '_', -1)) IN ('W', 'TS', 'CSC');
