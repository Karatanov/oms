#!/usr/bin/env bash
# Run on the OMS VPS before a coordinated source refresh. Never print secrets.
set -euo pipefail
umask 077
cd /docker/oms
backup="/docker/oms/backups/registry-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$backup"
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --routines --triggers --events --hex-blob --no-tablespaces "$MYSQL_DATABASE"' | gzip > "$backup/database.sql.gz"
gzip -t "$backup/database.sql.gz"
test -s "$backup/database.sql.gz"
docker compose exec -T oms tar -C /var/lib/oms -czf - uploads > "$backup/uploads.tar.gz"
tar -tzf "$backup/uploads.tar.gz" > "$backup/uploads-index.txt"
docker compose exec -T oms sh -c 'find /var/lib/oms/uploads -type f -exec sha256sum {} \;' > "$backup/upload-checksums.txt"
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch "$MYSQL_DATABASE"' > "$backup/protected-counts.tsv" <<'SQL'
SELECT 'users',COUNT(*) FROM users;
SELECT 'inspection_reports',COUNT(*) FROM inspection_reports;
SELECT 'projects',COUNT(*) FROM projects;
SELECT uuid,project_id,created_by FROM inspection_reports ORDER BY uuid;
SQL
(cd "$backup" && sha256sum database.sql.gz uploads.tar.gz > SHA256SUMS)
printf 'Backup created: %s\n' "$backup"
cat "$backup/protected-counts.tsv" | head -7
