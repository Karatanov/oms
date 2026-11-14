#!/usr/bin/env bash
# Guarded, source-verified repair. First restore a backup and trial the exact SQL.
set -euo pipefail
umask 077
cd /docker/oms
backup="${1:?verified backup directory}"
mode="${2:?trial or apply}"
case "$backup" in /docker/oms/backups/registry-*) ;; *) exit 2 ;; esac
test -s "$backup/database.sql.gz"
test -s "$backup/sector-repair.sql"
mysql_admin() {
 docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --batch "$@"' sh "$@"
}
protected() {
 mysql_admin "$1" <<'SQL'
SELECT 'users',COUNT(*) FROM users;
SELECT 'inspection_reports',COUNT(*) FROM inspection_reports;
SELECT 'projects',COUNT(*) FROM projects;
SELECT uuid,project_id,created_by FROM inspection_reports ORDER BY uuid;
SQL
}
case "$mode" in
 trial)
  (cd "$backup" && sha256sum -c SHA256SUMS)
  scratch="oms_sector_trial_$(date -u +%Y%m%d%H%M%S)"
  mysql_admin -e "CREATE DATABASE \`$scratch\` CHARACTER SET utf8mb4;"
  gzip -dc "$backup/database.sql.gz" | mysql_admin "$scratch"
  protected "$scratch" > "$backup/sector-restored-counts.tsv"
  cmp "$backup/protected-counts.tsv" "$backup/sector-restored-counts.tsv"
  mysql_admin "$scratch" < "$backup/sector-repair.sql" | tee "$backup/sector-trial-result.tsv"
  protected "$scratch" > "$backup/sector-trial-counts.tsv"
  cmp "$backup/protected-counts.tsv" "$backup/sector-trial-counts.tsv"
  printf '%s\n' "$scratch" > "$backup/sector-trial-database.txt"
  sha256sum "$backup/sector-repair.sql" > "$backup/sector-trial-plan.sha256"
  ;;
 apply)
  test -s "$backup/sector-trial-database.txt"
  sha256sum -c "$backup/sector-trial-plan.sha256"
  mysql_admin -e 'SELECT DATABASE()' >/dev/null
  docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --batch "$MYSQL_DATABASE"' < "$backup/sector-repair.sql" | tee "$backup/sector-live-result.tsv"
  docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch "$MYSQL_DATABASE"' > "$backup/sector-post-counts.tsv" <<'SQL'
SELECT 'users',COUNT(*) FROM users;
SELECT 'inspection_reports',COUNT(*) FROM inspection_reports;
SELECT 'projects',COUNT(*) FROM projects;
SELECT uuid,project_id,created_by FROM inspection_reports ORDER BY uuid;
SQL
  cmp "$backup/protected-counts.tsv" "$backup/sector-post-counts.tsv"
  docker compose exec -T oms sh -c 'find /var/lib/oms/uploads -type f -exec sha256sum {} \;' > "$backup/sector-post-upload-checksums.txt"
  cmp "$backup/upload-checksums.txt" "$backup/sector-post-upload-checksums.txt"
  ;;
 *) exit 2 ;;
esac
