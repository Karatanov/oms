#!/usr/bin/env bash
# Read-only registry references, scoped to the requested table columns.
set -euo pipefail
cd /docker/oms
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --batch --raw --skip-column-names "$MYSQL_DATABASE"' <<'SQL'
START TRANSACTION READ ONLY;
SELECT JSON_OBJECT('code',p.site_number,'dream',JSON_UNQUOTE(JSON_EXTRACT(d.registry_json,'$.dreamUrl')),
 'ded',JSON_UNQUOTE(JSON_EXTRACT(d.registry_json,'$.dedUrl')),
 'estimate',JSON_UNQUOTE(JSON_EXTRACT(d.registry_json,'$.estimateUrl')),
 'expertise',JSON_UNQUOTE(JSON_EXTRACT(d.registry_json,'$.expertiseUrl')))
FROM projects p JOIN project_monitoring_details d ON d.project_id=p.id
WHERE p.project_type IN ('subproject','subproject_part') AND JSON_VALID(d.registry_json)
ORDER BY p.site_number;
COMMIT;
SQL
