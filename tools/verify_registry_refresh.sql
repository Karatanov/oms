-- Run against the trial database first. No data are changed.
SELECT project_type, tranche_number, status, COUNT(*) AS records
FROM projects GROUP BY project_type,tranche_number,status;
SELECT COUNT(*) AS orphan_inspections FROM inspection_reports r
LEFT JOIN projects p ON p.id=r.project_id WHERE p.id IS NULL;
SELECT COUNT(*) AS orphan_contracts FROM procurement_records c
LEFT JOIN projects p ON p.id=c.project_id WHERE p.id IS NULL;
SELECT COUNT(*) AS duplicate_codes FROM (
 SELECT UPPER(TRIM(site_number)) AS code FROM projects
 WHERE project_type IN ('subproject','subproject_part')
 GROUP BY UPPER(TRIM(site_number)) HAVING COUNT(*)>1
) duplicates;
SELECT COUNT(*) AS missing_contract_links FROM procurement_records
WHERE contract_link IS NULL OR TRIM(contract_link)='';
SELECT tender_id AS source_without_contract_link FROM procurement_records
WHERE contract_link IS NULL OR TRIM(contract_link)='';
SELECT COUNT(*) AS payments, SUM(amount_uah) AS paid_uah,
 SUM(amount_eur) AS paid_eur FROM payment_monitoring_transactions;
SELECT MAX(dashboard_amount_paid_total_uah) AS approved_dashboard_total_uah
FROM payment_monitoring_rows;
SELECT COUNT(*) AS summaries, SUM(paid_uah) AS summary_paid_uah
FROM payment_monitoring_rows WHERE source_code NOT REGEXP '_(W|TS|CSC)([$].*)?$';
SELECT source_code FROM payment_monitoring_rows
WHERE source_code NOT REGEXP '^[A-Za-z]{2}[0-9]{2}_[0-9]+([#]Lot[0-9]+)?(_(W|TS|CSC))?$';
