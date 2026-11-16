#!/usr/bin/env bash
# Aggregate-only diagnostic: no users, identifiers, credentials or report content.
set -euo pipefail
cd /docker/oms
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --batch --raw --skip-column-names "$MYSQL_DATABASE"' <<'SQL'
START TRANSACTION READ ONLY;
SELECT JSON_OBJECT('currency',currency,'records',COUNT(*),
 'missingSavedEur',SUM(amount_eur_cents IS NULL),
 'missingRate',SUM(eur_exchange_rate IS NULL OR eur_exchange_rate<=0),
 'sentinelRate',SUM(eur_exchange_rate=1),
 'savedEurCents',SUM(amount_eur_cents))
FROM financial_records WHERE record_type IN ('payment','advance') GROUP BY currency;
COMMIT;
SQL
