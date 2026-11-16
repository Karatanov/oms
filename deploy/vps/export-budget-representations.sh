#!/usr/bin/env bash
# Read-only, including frozen budget metadata. Never exports users or report contents.
set -euo pipefail
cd /docker/oms
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --batch --raw --skip-column-names "${1:-$MYSQL_DATABASE}"' sh "${1:-}" <<'SQL'
START TRANSACTION READ ONLY;
SELECT JSON_OBJECT('id',p.id,'code',p.site_number,'type',p.project_type,'budget',p.budget_planned,'currency',p.currency,
 'sourceLot',d.source_lot_id,'workbook',d.source_workbook,'registry',d.registry_json,
 'snapshotId',a.id,'amount',CAST(a.amount AS CHAR),'snapshotCurrency',a.currency,
 'converted',CAST(a.converted_amount AS CHAR),'rate',CAST(a.uah_per_eur AS CHAR),
 'rateDate',a.rate_date,'edited',a.conversion_edited)
FROM projects p LEFT JOIN project_monitoring_details d ON d.project_id=p.id
LEFT JOIN project_amounts a ON a.project_id=p.id AND a.amount_kind='budget'
WHERE p.project_type IN ('subproject','subproject_part') ORDER BY p.id,a.id;
COMMIT;
SQL
