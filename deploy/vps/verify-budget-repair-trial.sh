#!/usr/bin/env bash
# Execute a reviewed plan only against a fresh restore, never the production schema.
set -euo pipefail
umask 077
cd /docker/oms
backup="${1:?absolute backup directory required}"
plan="${2:?absolute SQL plan required}"
case "$backup" in /docker/oms/backups/registry-*) ;; *) exit 2 ;; esac
case "$plan" in /docker/oms/budget-repair-*.sql) ;; *) exit 2 ;; esac
test -f "$backup/restore-verification.txt"
test -s "$plan"
(cd "$backup" && sha256sum -c SHA256SUMS)
scratch="oms_budgettrial_$(date -u +%Y%m%d%H%M%S)"
result="$backup/$scratch"
mkdir "$result"
cp "$plan" "$result/plan.sql"
(cd "$result" && sha256sum plan.sql > plan.sha256)
mysql_admin() {
  docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --batch --raw --skip-column-names "$@"' sh "$@"
}
mysql_admin -e "CREATE DATABASE \`$scratch\` CHARACTER SET utf8mb4;"
gzip -dc "$backup/database.sql.gz" | mysql_admin "$scratch"
invariants() {
  # Every other table must remain byte-identical in a deterministic logical dump.
  docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --skip-comments --skip-add-locks --order-by-primary --hex-blob --no-tablespaces --ignore-table="$1.projects" --ignore-table="$1.project_amounts" "$1"' sh "$scratch" | sha256sum
  for table in projects project_amounts; do
    excluded="'budget_planned'"
    if [ "$table" = project_amounts ]; then excluded="'amount','converted_amount'"; fi
    # Hash all columns other than the explicitly permitted repair fields.
    columns=$(mysql_admin "$scratch" -e "SET SESSION group_concat_max_len=65535; SELECT GROUP_CONCAT(CONCAT('\`',COLUMN_NAME,'\`') ORDER BY ORDINAL_POSITION) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$scratch' AND TABLE_NAME='$table' AND COLUMN_NAME NOT IN ($excluded);")
    mysql_admin "$scratch" -e "SELECT JSON_ARRAY($columns) FROM \`$table\` ORDER BY id;" | sha256sum
    mysql_admin "$scratch" -e "SHOW CREATE TABLE \`$table\`;" | sha256sum
  done
}
bash /docker/oms/export-budget-representations.sh "$scratch" > "$result/before.jsonl"
invariants > "$result/invariants-before.sha256"
mysql_admin "$scratch" < "$result/plan.sql" > "$result/applied-counts.tsv"
invariants > "$result/invariants-after.sha256"
cmp "$result/invariants-before.sha256" "$result/invariants-after.sha256"
bash /docker/oms/export-budget-representations.sh "$scratch" > "$result/after.jsonl"
mysql_admin -e "DROP DATABASE \`$scratch\`;"
printf 'Isolated budget trial passed; non-target data and protected columns unchanged.\n' > "$result/verified.txt"
printf 'Trial results: %s\n' "$result"
cat "$result/applied-counts.tsv"
