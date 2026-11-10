#!/usr/bin/env bash
set -euo pipefail
cd /docker/oms
backup="${1:?backup directory}"
case "$backup" in /docker/oms/backups/registry-*) ;; *) exit 2 ;; esac
if [ "${2:-trial}" = live ]; then
  scratch=""
else
  scratch="$(cat "$backup/trial-database.txt")"
  case "$scratch" in oms_registry_trial_[0-9]*) ;; *) exit 2 ;; esac
fi
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch "${1:-$MYSQL_DATABASE}"' sh "$scratch" < "$backup/verify.sql"
