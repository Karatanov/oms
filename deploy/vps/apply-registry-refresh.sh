#!/usr/bin/env bash
# Explicit operator cutover after a successful trial. Never used by app startup.
set -euo pipefail
umask 077
cd /docker/oms
backup="${1:?fresh verified backup directory}"
case "$backup" in /docker/oms/backups/registry-*) ;; *) exit 2 ;; esac
test -f "$backup/restore-verification.txt"
test -f "$backup/runner/RegistryRefreshRunner.class"
test -f "$backup/trial-approved.txt"
rollback() {
  local status=$?
  if [ "$status" -ne 0 ]; then
    printf 'Import failed. Restoring the pre-import SQL snapshot.\n' >&2
    if gzip -dc "$backup/database.sql.gz" | docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$MYSQL_DATABASE"'; then
      docker compose start oms
      printf 'Database restored. Review import.log before retrying.\n' >&2
    else
      printf 'RESTORE FAILED: application remains stopped for recovery.\n' >&2
    fi
  fi
  exit "$status"
}
# Prevent edits and scheduled refreshes racing the multi-registry cutover.
docker compose stop oms
trap rollback EXIT
docker compose run --pull never --rm --no-deps -T \
  -v "$backup/runner:/tmp/registry-runner:ro" oms \
  java -Xmx768m -cp '/opt/oms/lib/*:/tmp/registry-runner' RegistryRefreshRunner apply /tmp/registry-runner \
  | tee "$backup/import.log"
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch "$MYSQL_DATABASE"' > "$backup/post-import-protected.tsv" <<'SQL'
SELECT 'users',COUNT(*) FROM users;
SELECT 'inspection_reports',COUNT(*) FROM inspection_reports;
SELECT uuid,project_id,created_by FROM inspection_reports ORDER BY uuid;
SQL
# Project rows may be added by the source refresh. Users and inspection links may not.
sed '/^projects\t/ d' "$backup/protected-counts.tsv" > "$backup/pre-import-protected.tsv"
cmp "$backup/pre-import-protected.tsv" "$backup/post-import-protected.tsv"
docker compose start oms
trap - EXIT
printf 'Registry refresh applied. Application restarted.\n'
