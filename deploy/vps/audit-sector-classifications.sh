#!/usr/bin/env bash
# Read-only sector identities used by the financial progress aggregation.
set -euo pipefail
cd /docker/oms
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --batch --raw "$MYSQL_DATABASE"' <<'SQL'
START TRANSACTION READ ONLY;
SELECT p.id,p.site_number,p.project_type,p.status,p.sector,d.priority_area_source,
 d.source_workbook,d.source_rows
FROM projects p LEFT JOIN project_monitoring_details d ON d.project_id=p.id
WHERE p.project_type IN ('subproject','subproject_part') ORDER BY p.site_number,p.id;
COMMIT;
SQL
