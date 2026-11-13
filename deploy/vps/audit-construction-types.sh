#!/usr/bin/env bash
# Read-only classification evidence. No project names, addresses or credentials.
set -euo pipefail
cd /docker/oms
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --batch --raw "$MYSQL_DATABASE"' <<'SQL'
START TRANSACTION READ ONLY;
SELECT p.id,p.site_number,p.project_type,p.status,p.parent_project_id,p.construction_type,
 d.source_workbook,JSON_UNQUOTE(JSON_EXTRACT(d.registry_json,'$.constructionType')) AS source_construction_type
FROM projects p LEFT JOIN project_monitoring_details d ON d.project_id=p.id
WHERE p.project_type IN ('subproject','subproject_part')
ORDER BY p.site_number,p.id;
COMMIT;
SQL
