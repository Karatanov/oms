#!/usr/bin/env bash
set -euo pipefail
cd /docker/oms
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --batch --raw --skip-column-names "$MYSQL_DATABASE"' <<'SQL'
START TRANSACTION READ ONLY;
SELECT JSON_OBJECT('id',p.id,'code',p.site_number,'sourceLot',d.source_lot_id,'workbook',d.source_workbook,
 'registry',d.registry_json,'completedWorkPct',d.completed_work_pct,
 'financingUsedPct',d.financing_used_pct,'paidFinancingPct',d.paid_financing_pct)
FROM projects p JOIN project_monitoring_details d ON d.project_id=p.id
WHERE JSON_VALID(d.registry_json) AND d.source_workbook='DB_Monitoring table.xlsx' ORDER BY p.id;
COMMIT;
SQL
