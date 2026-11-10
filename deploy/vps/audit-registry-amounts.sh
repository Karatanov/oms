#!/usr/bin/env bash
# Read-only diagnosis of legacy numeric attributes versus the retained source payload.
set -euo pipefail
cd /docker/oms
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch "$MYSQL_DATABASE"' <<'SQL'
START TRANSACTION READ ONLY;
SELECT p.project_type,p.status,COUNT(*) AS records,
 SUM(p.budget_planned=0) AS zero_budgets,
 SUM(p.subproject_contract_amount IS NULL) AS missing_contract_totals
FROM projects p GROUP BY p.project_type,p.status;
SELECT p.site_number,p.project_type,p.budget_planned,p.subproject_contract_amount,
 JSON_UNQUOTE(JSON_EXTRACT(d.registry_json,'$.fundingTotal')) AS source_budget,
 JSON_UNQUOTE(JSON_EXTRACT(d.registry_json,'$.contractedTotal')) AS source_contract,
 d.source_workbook
FROM projects p JOIN project_monitoring_details d ON d.project_id=p.id
WHERE JSON_VALID(d.registry_json) AND
 (p.budget_planned=0 OR p.subproject_contract_amount IS NULL)
ORDER BY p.site_number LIMIT 20;
COMMIT;
SQL
