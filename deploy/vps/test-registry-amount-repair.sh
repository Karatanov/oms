#!/usr/bin/env bash
# Trial only: restores a fresh backup into an isolated schema and retains it for verification.
set -euo pipefail
umask 077
cd /docker/oms
backup="${1:?verified backup directory}"
case "$backup" in /docker/oms/backups/registry-*) ;; *) exit 2 ;; esac
test -f "$backup/restore-verification.txt"
test -s "$backup/amount-repair.sql"
scratch="oms_amount_trial_$(date -u +%Y%m%d%H%M%S)"
mysql_admin() {
  docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch "$@"' sh "$@"
}
mysql_admin -e "CREATE DATABASE \`$scratch\` CHARACTER SET utf8mb4;"
gzip -dc "$backup/database.sql.gz" | mysql_admin "$scratch"
mysql_admin "$scratch" < "$backup/amount-repair.sql" | tee "$backup/amount-trial-result.tsv"
sha256sum "$backup/amount-repair.sql" > "$backup/amount-trial-plan.sha256"
printf '%s\n' "$scratch" > "$backup/amount-trial-database.txt"
printf 'Trial retained: %s\n' "$scratch"
