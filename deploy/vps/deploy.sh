#!/usr/bin/env bash

set -Eeuo pipefail

image_tag="${1:?usage: deploy.sh <image-tag> <expected-revision>}"
expected_revision="${2:?usage: deploy.sh <image-tag> <expected-revision>}"
deploy_dir="/docker/oms"

if [[ ! "$image_tag" =~ ^sha-[0-9a-f]{40}$ ]]; then
  echo "Refusing unexpected image tag: $image_tag" >&2
  exit 2
fi
if [[ ! "$expected_revision" =~ ^[0-9a-f]{40}$ ]]; then
  echo "Refusing unexpected revision: $expected_revision" >&2
  exit 2
fi

cd "$deploy_dir"
test -f .env
test -f docker-compose.yml

previous_tag="$(sed -n 's/^OMS_IMAGE_TAG=//p' .env | tail -n 1)"
if [[ -z "$previous_tag" ]]; then
  previous_tag="latest"
fi

backup_file=".env.before-auto-${expected_revision:0:12}-$(date -u +%Y%m%dT%H%M%SZ)"
cp -p .env "$backup_file"
switched=0

wait_for_health() {
  local expected="$1"
  local container_id
  local status

  for _ in $(seq 1 36); do
    container_id="$(docker compose ps -q oms 2>/dev/null || true)"
    status="$(docker inspect "$container_id" --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' 2>/dev/null || true)"
    if [[ "$status" == "$expected" ]]; then
      return 0
    fi
    if [[ "$status" == "unhealthy" || "$status" == "exited" || "$status" == "dead" ]]; then
      return 1
    fi
    sleep 5
  done
  return 1
}

rollback() {
  local exit_code=$?
  trap - ERR
  set +e

  echo "Deployment failed; restoring image tag $previous_tag" >&2
  cp -p "$backup_file" .env
  if [[ "$switched" -eq 1 ]]; then
    docker compose up -d --no-deps --pull never oms
    wait_for_health healthy
  fi
  docker compose logs --tail 150 oms >&2 || true
  exit "$exit_code"
}
trap rollback ERR

if grep -q '^OMS_IMAGE_TAG=' .env; then
  sed -i "s/^OMS_IMAGE_TAG=.*/OMS_IMAGE_TAG=$image_tag/" .env
else
  printf '\nOMS_IMAGE_TAG=%s\n' "$image_tag" >> .env
fi

# Pull before replacing the healthy container. The workflow logs in to GHCR
# with its short-lived GITHUB_TOKEN immediately before invoking this script.
docker compose pull oms
switched=1
docker compose up -d --no-deps oms

if ! wait_for_health healthy; then
  echo "New OMS container did not become healthy" >&2
  false
fi

container_id="$(docker compose ps -q oms)"
actual_revision="$(docker inspect "$container_id" --format '{{index .Config.Labels "org.opencontainers.image.revision"}}')"
if [[ "$actual_revision" != "$expected_revision" ]]; then
  echo "Revision mismatch: expected $expected_revision, got $actual_revision" >&2
  false
fi

docker exec "$container_id" wget -qO- http://127.0.0.1:8080/health >/dev/null

public_ready=0
for _ in $(seq 1 12); do
  if wget -qO- https://ua-oms.com/health >/dev/null; then
    public_ready=1
    break
  fi
  sleep 5
done
if [[ "$public_ready" -ne 1 ]]; then
  echo "Public HTTPS health check failed" >&2
  false
fi

trap - ERR
echo "Deployed $expected_revision with image tag $image_tag"
