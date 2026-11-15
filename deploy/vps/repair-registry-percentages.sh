#!/usr/bin/env bash
# Trial the exact hash-locked plan on a restored copy, then apply optimistic updates atomically.
set -euo pipefail
umask 077
cd /docker/oms
backup="${1:?verified backup directory}"
mode="${2:?trial or apply}"
case "$backup" in /docker/oms/backups/registry-*) ;; *) exit 2 ;; esac
test -s "$backup/restore-verification.txt"
test -s "$backup/percentage-repair.sql"
mysql_admin() {
 docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --batch "$@"' sh "$@"
}
other_data() {
 docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --no-create-info --skip-comments --skip-add-locks --skip-disable-keys --hex-blob --no-tablespaces --ignore-table="$1.project_monitoring_details" "$1"' sh "$1"
}
# Retrieve the configured name inside the container without exposing credentials.
live="$(docker compose exec -T mysql sh -c 'printf "%s" "$MYSQL_DATABASE"')"
[[ "$live" =~ ^[a-zA-Z0-9_]+$ ]] || exit 2
case "$mode" in
 trial)
  (cd "$backup" && sha256sum -c SHA256SUMS)
  scratch="oms_percentage_trial_$(date -u +%Y%m%d%H%M%S)"
  mysql_admin -e "CREATE DATABASE \`$scratch\` CHARACTER SET utf8mb4;"
  gzip -dc "$backup/database.sql.gz" | mysql_admin "$scratch"
  other_data "$scratch" > "$backup/percentage-trial-other-before.sql"
  mysql_admin "$scratch" < "$backup/percentage-repair.sql" > "$backup/percentage-trial-result.tsv"
  other_data "$scratch" > "$backup/percentage-trial-other-after.sql"
  cmp "$backup/percentage-trial-other-before.sql" "$backup/percentage-trial-other-after.sql"
  # Verify all columns except the three typed percentages, plus every non-percentage JSON key.
  result="$(mysql_admin "$live" <<SQL
START TRANSACTION READ ONLY;
SET SESSION group_concat_max_len=100000;
SELECT GROUP_CONCAT(CONCAT('NOT (p.',CHAR(96),COLUMN_NAME,CHAR(96),' <=> t.',CHAR(96),COLUMN_NAME,CHAR(96),')') SEPARATOR ' OR ')
INTO @differences FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
AND TABLE_NAME='project_monitoring_details'
AND COLUMN_NAME NOT IN ('registry_json','completed_work_pct','financing_used_pct','paid_financing_pct');
SET @query=CONCAT('SELECT COUNT(*) AS unexpected_detail_changes FROM project_monitoring_details p JOIN $scratch.project_monitoring_details t ON t.project_id=p.project_id WHERE ',@differences);
PREPARE comparison FROM @query;
EXECUTE comparison;
DEALLOCATE PREPARE comparison;
SELECT COUNT(*) AS unexpected_payload_changes FROM project_monitoring_details p JOIN $scratch.project_monitoring_details t ON t.project_id=p.project_id
WHERE NOT(JSON_REMOVE(p.registry_json,'$.approvedFundingPct','$.completedWorkPct','$.financingUsedPct','$.paidFinancingPct')
 <=> JSON_REMOVE(t.registry_json,'$.approvedFundingPct','$.completedWorkPct','$.financingUsedPct','$.paidFinancingPct'));
SELECT ABS((SELECT COUNT(*) FROM project_monitoring_details)-(SELECT COUNT(*) FROM $scratch.project_monitoring_details)) AS unexpected_row_count_changes;
COMMIT;
SQL
  )"
  printf '%s\n' "$result" > "$backup/percentage-trial-scope.tsv"
  printf '%s\n' "$result" | awk '/^unexpected_/ { getline; if ($0!=0) failed=1; checked++ } END { exit (failed || checked!=3) }'
  sha256sum "$backup/percentage-repair.sql" > "$backup/percentage-trial-plan.sha256"
  printf '%s\n' "$scratch" > "$backup/percentage-trial-database.txt"
  printf 'Isolated restore, full other-table dumps and percentage-only scope verified.\n' > "$backup/percentage-trial-verification.txt"
  cat "$backup/percentage-trial-result.tsv" "$backup/percentage-trial-scope.tsv"
  ;;
 apply)
  test -s "$backup/percentage-trial-verification.txt"
  scratch="$(cat "$backup/percentage-trial-database.txt")"
  [[ "$scratch" =~ ^oms_percentage_trial_[0-9]+$ ]] || exit 2
  sha256sum -c "$backup/percentage-trial-plan.sha256"
  other_data "$live" > "$backup/percentage-live-other-before.sql"
  mysql_admin "$live" < "$backup/percentage-repair.sql" > "$backup/percentage-live-result.tsv"
  other_data "$live" > "$backup/percentage-live-other-after.sql"
  cmp "$backup/percentage-live-other-before.sql" "$backup/percentage-live-other-after.sql"
  docker compose exec -T oms sh -c 'find /var/lib/oms/uploads -type f -exec sha256sum {} \;' > "$backup/percentage-post-upload-checksums.txt"
  cmp "$backup/upload-checksums.txt" "$backup/percentage-post-upload-checksums.txt"
  # Entire repaired table must match the verified trial, not merely its row count.
  for database in "$live" "$scratch"; do
   docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --no-create-info --skip-comments --skip-add-locks --skip-disable-keys --hex-blob --no-tablespaces "$1" project_monitoring_details' sh "$database" > "$backup/percentage-details-$database.sql"
  done
  cmp "$backup/percentage-details-$live.sql" "$backup/percentage-details-$scratch.sql"
  cat "$backup/percentage-live-result.tsv"
  printf 'Percentage repair applied; all other tables and uploaded files unchanged.\n'
  ;;
 *) exit 2 ;;
esac
