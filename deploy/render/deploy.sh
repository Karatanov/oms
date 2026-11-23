#!/usr/bin/env bash
# Release or roll back the existing image-backed service to one audited commit.
set -euo pipefail
sha="${1:?expected full Git SHA}"
[[ "$sha" =~ ^[0-9a-f]{40}$ ]] || exit 2
: "${RENDER_DEPLOY_HOOK_URL:?set the usif environment secret}"
base=https://oms-3j46.onrender.com
curl --fail --silent --show-error --max-time 30 --get --output /dev/null \
  --data-urlencode "imgURL=ghcr.io/karatanov/oms:sha-$sha" "$RENDER_DEPLOY_HOOK_URL"
for attempt in $(seq 1 90); do
  if [[ "$(curl -fsS --max-time 10 "$base/version" | jq -r .revision)" == "${sha:0:12}" ]] &&
     curl -fsS --max-time 10 "$base/health" | jq -e '.status == "UP"' >/dev/null &&
     curl -fsS --max-time 10 "$base/ready" | jq -e '.status == "READY"' >/dev/null; then
    page=$(curl -fsS --max-time 15 "$base/")
    [[ "$page" == *'data-oms-name="USIF"'* && "$page" == *'id="login-form"'* && "$page" != *'ua-oms.com'* ]]
    echo "Verified $base at $sha"
    exit 0
  fi
  sleep 8
done
echo "The requested revision did not become ready" >&2
exit 1
