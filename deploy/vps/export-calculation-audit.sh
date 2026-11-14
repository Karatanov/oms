#!/usr/bin/env bash
# Read-only financial source evidence. Never exports users, tokens or report contents.
set -euo pipefail
cd /docker/oms
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --batch --raw --skip-column-names "$MYSQL_DATABASE"' <<'SQL'
START TRANSACTION READ ONLY;
SELECT JSON_OBJECT('kind','project','id',p.id,'code',p.site_number,'type',p.project_type,'status',p.status,
 'parent',p.parent_project_id,'region',p.region,'sector',p.sector,'constructionType',p.construction_type,
 'budget',p.budget_planned,'contract',p.subproject_contract_amount,'registry',d.registry_json,
 'priority',d.priority_area_source,'completed',d.completed_work_pct)
FROM projects p LEFT JOIN project_monitoring_details d ON d.project_id=p.id;
SELECT JSON_OBJECT('kind','payment','code',source_code,'total',total_investment_uah,'eib',eib_investment_uah,
 'local',local_co_financing_uah,'inProgress',paid_and_in_progress_uah,'paid',paid_uah,'remaining',remaining_uah,
 'plannedPct',planned_eib_share_pct,'actualPct',factual_eib_share_pct,'control',dashboard_amount_paid_total_uah)
FROM payment_monitoring_rows;
SELECT JSON_OBJECT('kind','transaction','code',source_code,'ref',source_reference,'date',payment_date,
 'batch',batch_id,'uah',amount_uah,'eur',amount_eur) FROM payment_monitoring_transactions;
SELECT JSON_OBJECT('kind','procurement','id',id,'code',sub_project_id,'lot',sub_project_lot_id,
 'procurement',procurement_id,'tender',tender_id,'type',source_contract_type,'status',purchase_status,
 'amount',contract_amount_uah,'signed',signed_contract_amount_uah,'eibUah',estimated_eib_uah,
 'eibEur',estimated_eib_eur,'batch',batch_id) FROM procurement_records;
COMMIT;
SQL
