#!/usr/bin/env bash
# Restore the verified backup and exercise the real import services in isolation.
set -euo pipefail
umask 077
cd /docker/oms
backup="${1:?backup directory required}"
case "$backup" in /docker/oms/backups/registry-*) ;; *) exit 2 ;; esac
test -f "$backup/restore-verification.txt"
scratch="oms_registry_trial_$(date -u +%Y%m%d%H%M%S)"
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch' <<SQL
CREATE DATABASE \`$scratch\` CHARACTER SET utf8mb4;
SQL
gzip -dc "$backup/database.sql.gz" | docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch "$1"' sh "$scratch"
docker compose exec -T mysql sh -c '
  case "$MYSQL_USER" in *[!a-zA-Z0-9_]*) exit 2;; esac
  MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e "GRANT ALL ON \`$1\`.* TO '\''$MYSQL_USER'\''@'\''%'\'';"
' sh "$scratch"
printf '%s\n' "$scratch" > "$backup/trial-database.txt"
docker compose exec -T -e "DATABASE_URL=jdbc:mysql://mysql:3306/$scratch" oms \
  java -Xmx768m -cp '/opt/oms/lib/*:/tmp/registry-runner' RegistryRefreshRunner apply /tmp/registry-runner \
  | tee "$backup/trial-import.log"
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch "$1"' sh "$scratch" <<'SQL'
SELECT 'users', COUNT(*) FROM users;
SELECT 'reports', COUNT(*) FROM inspection_reports;
SELECT 'projects', COUNT(*) FROM projects;
SELECT 'procurement', COUNT(*) FROM procurement_records;
SELECT 'payment_summary', COUNT(*) FROM payment_monitoring_rows;
SELECT 'payment_transactions', COUNT(*) FROM payment_monitoring_transactions;
SQL
printf 'Trial database retained: %s\n' "$scratch"
