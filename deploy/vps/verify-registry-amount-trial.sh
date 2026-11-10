#!/usr/bin/env bash
set -euo pipefail
cd /docker/oms
backup="${1:?verified backup directory}"
case "$backup" in /docker/oms/backups/registry-*) ;; *) exit 2 ;; esac
scratch="$(cat "$backup/amount-trial-database.txt")"
[[ "$scratch" =~ ^oms_amount_trial_[0-9]+$ ]] || exit 2
case "${2:-amounts}" in
  amounts) allowed="'budget_planned','subproject_contract_amount','updated_at'" ;;
  currency) allowed="'currency','updated_at'" ;;
  *) exit 2 ;;
esac
result="$(docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch "$MYSQL_DATABASE"' <<SQL
START TRANSACTION READ ONLY;
SET SESSION group_concat_max_len=100000;
SELECT GROUP_CONCAT(CONCAT('NOT (p.',CHAR(96),COLUMN_NAME,CHAR(96),' <=> t.',CHAR(96),COLUMN_NAME,CHAR(96),')') SEPARATOR ' OR ')
INTO @differences FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='projects'
AND COLUMN_NAME NOT IN ($allowed);
SET @query=CONCAT('SELECT COUNT(*) AS unexpected_project_changes FROM projects p JOIN $scratch.projects t ON t.id=p.id WHERE ',@differences);
PREPARE comparison FROM @query;
EXECUTE comparison;
DEALLOCATE PREPARE comparison;
SELECT COUNT(*) AS matched_projects,SUM(p.budget_planned<>t.budget_planned) AS corrected_budgets,
 SUM(NOT(p.subproject_contract_amount <=> t.subproject_contract_amount)) AS corrected_contracts,
 SUM(NOT(p.updated_at <=> t.updated_at)) AS automatic_timestamp_updates,
 SUM(NOT(p.currency <=> t.currency)) AS corrected_currency_labels
FROM projects p JOIN $scratch.projects t ON t.id=p.id;
SELECT COUNT(*) AS unchanged_inspection_links FROM inspection_reports p JOIN $scratch.inspection_reports t
ON p.uuid=t.uuid AND p.project_id=t.project_id AND p.created_by=t.created_by;
COMMIT;
SQL
)"
printf '%s\n' "$result"
printf '%s\n' "$result" | awk '$0=="unexpected_project_changes" { getline; if ($0==0) verified=1 } END { exit !verified }'
printf 'Non-amount project fields verified.\n' > "$backup/amount-trial-verification.txt"
