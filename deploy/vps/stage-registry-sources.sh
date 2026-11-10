#!/usr/bin/env bash
set -euo pipefail
umask 077
cd /docker/oms
source_sql="${1:?staging SQL required}"
case "$source_sql" in /docker/oms/backups/registry-*/sources.sql) ;; *) exit 2 ;; esac
test -s "$source_sql"
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --show-warnings' < "$source_sql"
backup="$(dirname "$source_sql")"
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch "$MYSQL_DATABASE"' > "$backup/post-staging-counts.tsv" <<'SQL'
SELECT 'users',COUNT(*) FROM users;
SELECT 'inspection_reports',COUNT(*) FROM inspection_reports;
SELECT 'projects',COUNT(*) FROM projects;
SELECT uuid,project_id,created_by FROM inspection_reports ORDER BY uuid;
SQL
cmp "$backup/protected-counts.tsv" "$backup/post-staging-counts.tsv"
docker compose exec -T oms sha256sum -c - < "$backup/upload-checksums.txt" > "$backup/post-staging-upload-verification.txt"
printf 'Live user/report/project identities and uploaded files unchanged.\n'
