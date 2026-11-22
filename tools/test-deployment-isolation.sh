#!/usr/bin/env bash
# Run only against runner-owned containers with isolated networks and volumes.
set -euo pipefail
image=${1:?supply the SHA image published by this workflow}
[[ "$image" =~ ^ghcr\.io/karatanov/oms:sha-[a-f0-9]{40}$ ]]
run="oms-check-$(openssl rand -hex 5)"
tmp=$(mktemp -d)
cleanup() {
  for brand in ur3 usif; do
    docker rm -fv "$run-$brand-app" "$run-$brand-db" >/dev/null 2>&1 || true
    docker network rm "$run-$brand" >/dev/null 2>&1 || true
  done
}
trap cleanup EXIT
sql() {
  docker exec "$run-$1-db" mysql --batch --skip-column-names -uapp "-p$password" "oms_$1" -e "$2" 2>/dev/null
}
ready() {
  for tick in $(seq 1 120); do
    if curl -fsS --max-time 3 "$1/ready" 2>/dev/null | jq -e '.status == "READY"' >/dev/null; then return 0; fi
    sleep 2
  done
  docker logs "$2" --tail 80
  return 1
}
password=$(openssl rand -hex 24)
for brand in ur3 usif; do
  net="$run-$brand"
  docker network create "$net" >/dev/null
  docker run -d --name "$net-db" --network "$net" --network-alias db \
    -e MYSQL_RANDOM_ROOT_PASSWORD=yes -e MYSQL_DATABASE="oms_$brand" \
    -e MYSQL_USER=app -e MYSQL_PASSWORD="$password" mysql:8.4 >/dev/null
  for tick in $(seq 1 90); do
    if sql "$brand" 'SELECT 1' >/dev/null; then break; fi
    sleep 2
  done
  port=18181
  origin=https://ua-oms.com
  if [[ "$brand" == usif ]]; then port=18182; origin=https://oms-3j46.onrender.com; fi
  docker run -d --name "$net-app" --network "$net" -p "127.0.0.1:$port:8080" \
    -e OMS_BRAND="$brand" -e OMS_PUBLIC_BASE_URL="$origin" \
    -e "DATABASE_URL=jdbc:mysql://db:3306/oms_$brand?allowPublicKeyRetrieval=true&useSSL=false" \
    -e DATABASE_USER=app -e DATABASE_PASSWORD="$password" -e OMS_JWT_SECRET="$(openssl rand -hex 32)" \
    -e OMS_WEB_DIR=/opt/oms/web -e OMS_UPLOAD_DIR=/tmp/check-uploads \
    -e PROCUREMENT_AUTO_REFRESH_ENABLED=false -e SUBPROJECT_AUTO_REFRESH_ENABLED=false \
    "$image" >/dev/null
  ready "http://127.0.0.1:$port" "$net-app"
done
ur=http://127.0.0.1:18181
us=http://127.0.0.1:18182
# Scan the full set of uncompressed packaged public files, including JS/WASM.
docker cp "$run-ur3-app:/opt/oms/web" "$tmp/web"
if grep -railE --exclude='*.gz' --exclude='*.br' 'usif|oms-3j46\.onrender\.com' "$tmp/web"; then exit 1; fi
for path in / /login.html /app.html /index.html /privacy.html /terms.html /sitemap.xml /robots.txt /version /ready /health; do
  output=$(curl -fsS "$ur$path")
  if grep -qiE 'usif|oms-3j46\.onrender\.com' <<<"$output"; then exit 1; fi
done
curl -fsS "$ur/" > "$tmp/ur.html"
cmp "$tmp/ur.html" "$tmp/web/login.html"
curl -fsS "$us/" > "$tmp/us.html"
grep -q 'data-oms-name="USIF"' "$tmp/us.html"
grep -q 'Online Project Monitoring System' "$tmp/us.html"
if grep -qiE 'ua-oms.com|UR III' "$tmp/us.html"; then exit 1; fi
[[ "$(sql usif 'SELECT COUNT(*) FROM roles')" == 5 ]]
[[ "$(sql usif 'SELECT COUNT(*) FROM flyway_schema_history WHERE version="80" AND success=1')" == 1 ]]
for table in $(sql usif 'SHOW TABLES'); do
  case "$table" in roles|flyway_schema_history) continue;; esac
  [[ "$(sql usif "SELECT COUNT(*) FROM \`$table\`")" == 0 ]]
done
for brand in ur3 usif; do
  url=$ur
  if [[ "$brand" == usif ]]; then url=$us; fi
  curl -fsS -c "$tmp/$brand.jar" -X POST "$url/api/v1/auth/guest" > "$tmp/$brand.json"
  jwt=$(jq -er '.accessToken' "$tmp/$brand.json")
  curl -fsS -H "Authorization: Bearer $jwt" "$url/api/v1/auth/session" | jq -e .guest >/dev/null
  other=$us
  if [[ "$brand" == usif ]]; then other=$ur; fi
  cookie=$(awk '/oms_session/{print $6 "=" $7}' "$tmp/$brand.jar")
  [[ -n "$cookie" ]]
  [[ "$(curl -s -o /dev/null -w '%{http_code}' -H "Cookie: $cookie" -H "Authorization: Bearer $jwt" "$other/api/v1/auth/session")" == 401 ]]
done
# A one-time, randomly credentialed administrator exists only inside this fixture.
pw=$(openssl rand -hex 24)
hash=$(printf '%s\n' "$pw" | htpasswd -niBC 10 test | cut -d: -f2 | tr -d '\r\n' | sed 's/^\$2y\$/\$2a\$/')
sql usif "INSERT INTO users(uuid,username,email,password_hash,role_id) SELECT UUID(),'fixture','fixture@example.invalid','$hash',id FROM roles WHERE code='ADMIN'"
jwt=$(curl -fsS -H 'Content-Type: application/json' -d "{\"username\":\"fixture\",\"password\":\"$pw\"}" "$us/api/v1/auth/login" | jq -er .accessToken)
api() { curl -fsS -H "Authorization: Bearer $jwt" "$us/api/v1/$1"; }
api projects/map | jq -e 'length==0'
api dashboard/overview | jq -e '.nationalSummary.subprojectsCount==0'
api procurements | jq -e 'length==0'
api financials/payment-monitoring >/dev/null
[[ "$(sql usif 'SELECT COUNT(*) FROM payment_monitoring_rows')" == 0 ]]
ur_count=$(sql ur3 'SELECT COUNT(*) FROM projects')
curl -fsS -H "Authorization: Bearer $jwt" -H 'Content-Type: application/json' \
  -d '{"name":"Isolation fixture","siteName":"Fixture","siteNumber":"TEST","budgetPlanned":100}' "$us/api/v1/projects" >/dev/null
docker restart "$run-usif-app" >/dev/null
ready "$us" "$run-usif-app"
[[ "$(sql usif 'SELECT COUNT(*) FROM projects WHERE name="Isolation fixture"')" == 1 ]]
[[ "$(sql ur3 'SELECT COUNT(*) FROM projects')" == "$ur_count" ]]
[[ "$(sql ur3 'SELECT COUNT(*) FROM projects WHERE name="Isolation fixture"')" == 0 ]]
api projects | jq -e '.. | strings | select(.=="Isolation fixture")' >/dev/null
node tools/test-brand-pages.cjs
