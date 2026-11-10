#!/usr/bin/env bash
# Restore into an isolated database. The live schema is never selected.
set -euo pipefail
umask 077
cd /docker/oms
backup="${1:?absolute backup directory required}"
case "$backup" in /docker/oms/backups/registry-*) ;; *) exit 2 ;; esac
test -f "$backup/SHA256SUMS"
(cd "$backup" && sha256sum -c SHA256SUMS)
scratch="oms_restorecheck_$(date -u +%Y%m%d%H%M%S)"
mysql_admin() {
  docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch "$@"' sh "$@"
}
mysql_admin -e "CREATE DATABASE \`$scratch\` CHARACTER SET utf8mb4;"
# Retain the scratch schema on failure for diagnosis. Only the freshly created
# schema is dropped after the protected counts and report links match exactly.
gzip -dc "$backup/database.sql.gz" | mysql_admin "$scratch"
mysql_admin "$scratch" > "$backup/restored-counts.tsv" <<'SQL'
SELECT 'users',COUNT(*) FROM users;
SELECT 'inspection_reports',COUNT(*) FROM inspection_reports;
SELECT 'projects',COUNT(*) FROM projects;
SELECT uuid,project_id,created_by FROM inspection_reports ORDER BY uuid;
SQL
cmp "$backup/protected-counts.tsv" "$backup/restored-counts.tsv"
mysql_admin -e "DROP DATABASE \`$scratch\`;"
printf 'Restore verified; protected identities and counts match.\n' | tee "$backup/restore-verification.txt"
