#!/usr/bin/env bash
# Deploy one immutable OMS image without touching MySQL, uploads, .env or Traefik.
set -Eeuo pipefail

: "${OMS_IMAGE_TAG:?OMS_IMAGE_TAG is required}"
: "${OMS_IMAGE:?OMS_IMAGE is required}"

if [[ ! "$OMS_IMAGE_TAG" =~ ^sha-[0-9a-f]{40}$ ]]; then
  echo "Refusing non-immutable OMS_IMAGE_TAG." >&2
  exit 64
fi

DEPLOY_DIR="${OMS_DEPLOY_DIR:-/docker/oms}"
COMPOSE_FILE="$DEPLOY_DIR/docker-compose.yml"
ENV_FILE="$DEPLOY_DIR/.env"

[[ -f "$COMPOSE_FILE" ]] || { echo "Compose file not found: $COMPOSE_FILE" >&2; exit 66; }
[[ -f "$ENV_FILE" ]] || { echo "Production .env not found: $ENV_FILE" >&2; exit 66; }

compose() {
  OMS_IMAGE="$OMS_IMAGE" OMS_IMAGE_TAG="$OMS_IMAGE_TAG" \
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

# Pull and replace only the OMS service. Named MySQL/upload volumes and the
# separately managed Traefik stack are deliberately outside this operation.
compose pull oms
compose up -d --no-deps oms

container_id="$(compose ps -q oms)"
[[ -n "$container_id" ]] || { echo "OMS container was not created." >&2; exit 1; }

deadline=$((SECONDS + 180))
while (( SECONDS < deadline )); do
  state="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id")"
  case "$state" in
    healthy)
      echo "OMS container is healthy: $OMS_IMAGE:$OMS_IMAGE_TAG"
      exit 0
      ;;
    unhealthy|exited|dead)
      echo "OMS container entered state '$state'." >&2
      docker logs --tail 100 "$container_id" >&2 || true
      exit 1
      ;;
  esac
  sleep 5
done

echo "Timed out waiting for OMS container health." >&2
docker logs --tail 100 "$container_id" >&2 || true
exit 1
