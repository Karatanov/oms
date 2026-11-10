#!/usr/bin/env bash
# Apply only a previously trialled, guarded amount repair; never restore over concurrent edits.
set -euo pipefail
umask 077
cd /docker/oms
backup="${1:?verified backup directory}"
case "$backup" in /docker/oms/backups/registry-*) ;; *) exit 2 ;; esac
test -f "$backup/restore-verification.txt"
test -f "$backup/amount-trial-database.txt"
test -f "$backup/amount-trial-verification.txt"
test -f "$backup/amount-trial-plan.sha256"
test -s "$backup/amount-repair.sql"
sha256sum -c "$backup/amount-trial-plan.sha256"
# The SQL plan asserts its affected-row count before COMMIT. Any mismatch
# aborts the mysql batch connection, rolling back instead of overwriting edits.
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch "$MYSQL_DATABASE"' \
  < "$backup/amount-repair.sql" | tee "$backup/amount-live-result.tsv"
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch "$MYSQL_DATABASE"' \
  > "$backup/amount-post-protected.tsv" <<'SQL'
SELECT 'users',COUNT(*) FROM users;
SELECT 'inspection_reports',COUNT(*) FROM inspection_reports;
SELECT 'projects',COUNT(*) FROM projects;
SELECT uuid,project_id,created_by FROM inspection_reports ORDER BY uuid;
SQL
cmp "$backup/protected-counts.tsv" "$backup/amount-post-protected.tsv"
docker compose exec -T oms sh -c 'find /var/lib/oms/uploads -type f -exec sha256sum {} \;' \
  > "$backup/amount-post-upload-checksums.txt"
cmp "$backup/upload-checksums.txt" "$backup/amount-post-upload-checksums.txt"
printf 'Amount repair applied; protected identities and uploaded files unchanged.\n'
