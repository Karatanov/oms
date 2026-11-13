#!/usr/bin/env bash
# Read-only region identity evidence; no project names or personal data are exported.
set -euo pipefail
cd /docker/oms
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --batch "$MYSQL_DATABASE"' <<'SQL'
START TRANSACTION READ ONLY;
SELECT region,COUNT(*) AS subprojects FROM projects
WHERE project_type='subproject' GROUP BY region ORDER BY region;
COMMIT;
SQL
