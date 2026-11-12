#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd "$(dirname "$0")" && pwd)"
backup="${1:?verified backup directory}"
bash "$script_dir/verify-registry-amount-trial.sh" "$backup" amounts
scratch="$(cat "$backup/amount-trial-database.txt")"
[[ "$scratch" =~ ^oms_amount_trial_[0-9]+$ ]] || exit 2
cd /docker/oms
result="$(docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch "$MYSQL_DATABASE"' <<SQL
START TRANSACTION READ ONLY;
SET SESSION group_concat_max_len=100000;
SELECT GROUP_CONCAT(CONCAT('NOT (p.',CHAR(96),COLUMN_NAME,CHAR(96),' <=> t.',CHAR(96),COLUMN_NAME,CHAR(96),')') SEPARATOR ' OR ')
INTO @differences FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
AND TABLE_NAME='project_monitoring_details' AND COLUMN_NAME<>'registry_json';
SET @query=CONCAT('SELECT COUNT(*) AS unexpected_detail_changes FROM project_monitoring_details p JOIN $scratch.project_monitoring_details t ON t.project_id=p.project_id WHERE ',@differences);
PREPARE comparison FROM @query;
EXECUTE comparison;
DEALLOCATE PREPARE comparison;
SELECT COUNT(*) AS unexpected_nonmoney_payload_changes
FROM project_monitoring_details p JOIN $scratch.project_monitoring_details t ON t.project_id=p.project_id
WHERE NOT(JSON_REMOVE(p.registry_json,'$.fundingTotal','$.fundingWorks','$.fundingTs','$.fundingCsc','$.eibCost','$.localCost',
 '$.contractedTotal','$.contractedEib','$.contractedLocal','$.constructionAmount','$.constructionEib','$.constructionLocal','$.paidConstruction')
 <=> JSON_REMOVE(t.registry_json,'$.fundingTotal','$.fundingWorks','$.fundingTs','$.fundingCsc','$.eibCost','$.localCost',
 '$.contractedTotal','$.contractedEib','$.contractedLocal','$.constructionAmount','$.constructionEib','$.constructionLocal','$.paidConstruction'));
COMMIT;
SQL
)"
printf '%s\n' "$result"
printf '%s\n' "$result" | awk '/^unexpected_/ { getline; if ($0!=0) failed=1; checked++ } END { exit (failed || checked!=2) }'
printf 'Registry nonmoney values and metadata verified.\n' > "$backup/precision-trial-verification.txt"
