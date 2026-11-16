#!/usr/bin/env bash
# Apply only the exact plan that passed an isolated restore trial and before-state check.
set -euo pipefail
umask 077
cd /docker/oms
backup=$(realpath "${1:?backup directory required}")
trial=$(realpath "${2:?verified trial directory required}")
case "$backup" in /docker/oms/backups/registry-*) ;; *) exit 2 ;; esac
case "$trial" in "$backup"/oms_budgettrial_*) ;; *) exit 2 ;; esac
test -f "$backup/restore-verification.txt"
test -f "$trial/verified.txt"
test ! -e "$trial/live-applied.txt"
(cd "$backup" && sha256sum -c SHA256SUMS)
(cd "$trial" && sha256sum -c plan.sha256)
bash /docker/oms/export-budget-representations.sh > "$trial/live-before.jsonl"
# Any concurrent source or frozen-currency change requires a new plan and trial.
cmp "$trial/before.jsonl" "$trial/live-before.jsonl"
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --batch --raw --skip-column-names "$MYSQL_DATABASE"' < "$trial/plan.sql" > "$trial/live-counts.tsv"
bash /docker/oms/export-budget-representations.sh > "$trial/live-after.jsonl"
printf 'Guarded transaction committed; external after-state verification is still required.\n' > "$trial/live-applied.txt"
cat "$trial/live-counts.tsv"
printf 'Live verification export: %s/live-after.jsonl\n' "$trial"
