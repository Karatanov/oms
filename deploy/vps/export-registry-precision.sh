#!/usr/bin/env bash
set -euo pipefail
cd /docker/oms
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --batch --raw --skip-column-names "${1:-$MYSQL_DATABASE}"' sh "${1:-}" <<'SQL'
START TRANSACTION READ ONLY;
SELECT JSON_OBJECT('id',p.id,'code',d.source_lot_id,'workbook',d.source_workbook,
 'budget',p.budget_planned,'contract',p.subproject_contract_amount,'registry',d.registry_json)
FROM projects p JOIN project_monitoring_details d ON d.project_id=p.id
WHERE JSON_VALID(d.registry_json)
AND d.source_workbook IN ('DB_Monitoring table.xlsx','UR III AT&PP Allocation Table.xlsx') ORDER BY p.id;
COMMIT;
SQL
