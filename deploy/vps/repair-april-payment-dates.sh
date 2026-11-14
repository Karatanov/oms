#!/usr/bin/env bash
# Restore the backup and trial the same hash-locked plan before touching live data.
set -euo pipefail
umask 077
cd /docker/oms
backup="${1:?verified backup directory}"
mode="${2:?trial or apply}"
case "$backup" in /docker/oms/backups/registry-*) ;; *) exit 2 ;; esac
test -s "$backup/database.sql.gz"
test -s "$backup/payment-repair.sql"
mysql_admin() {
 docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --batch "$@"' sh "$@"
}
case "$mode" in
 trial)
  (cd "$backup" && sha256sum -c SHA256SUMS)
  scratch="oms_payment_trial_$(date -u +%Y%m%d%H%M%S)"
  mysql_admin -e "CREATE DATABASE \`$scratch\` CHARACTER SET utf8mb4;"
  gzip -dc "$backup/database.sql.gz" | mysql_admin "$scratch"
  mysql_admin "$scratch" < "$backup/payment-repair.sql" > "$backup/payment-trial-result.tsv"
  # Exact before/after snapshots of every other table, including users and SIRs.
  printf '%s\n' "$scratch" > "$backup/payment-trial-database.txt"
  sha256sum "$backup/payment-repair.sql" > "$backup/payment-trial-plan.sha256"
  cat "$backup/payment-trial-result.tsv" | tail -5
  ;;
 apply)
  test -s "$backup/payment-trial-database.txt"
  sha256sum -c "$backup/payment-trial-plan.sha256"
  docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --no-create-info --skip-comments --skip-add-locks --skip-disable-keys --hex-blob --no-tablespaces --ignore-table="$MYSQL_DATABASE.payment_monitoring_transactions" "$MYSQL_DATABASE"' > "$backup/payment-other-data-before.sql"
  docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --batch "$MYSQL_DATABASE"' < "$backup/payment-repair.sql" > "$backup/payment-live-result.tsv"
  docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --no-create-info --skip-comments --skip-add-locks --skip-disable-keys --hex-blob --no-tablespaces --ignore-table="$MYSQL_DATABASE.payment_monitoring_transactions" "$MYSQL_DATABASE"' > "$backup/payment-other-data-after.sql"
  cmp "$backup/payment-other-data-before.sql" "$backup/payment-other-data-after.sql"
  docker compose exec -T oms sh -c 'find /var/lib/oms/uploads -type f -exec sha256sum {} \;' > "$backup/payment-post-upload-checksums.txt"
  cmp "$backup/upload-checksums.txt" "$backup/payment-post-upload-checksums.txt"
  cat "$backup/payment-live-result.tsv" | tail -5
  ;;
 *) exit 2 ;;
esac
