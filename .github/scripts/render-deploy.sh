#!/usr/bin/env bash
#
# Deploys a specific image to a Render service and blocks until the deploy is live (or fails).
# Used by both the Deploy and Rollback workflows so the "trigger + wait" logic lives in one place.
#
# Required env vars:
#   RENDER_API_KEY     Render API key (Account Settings → API Keys).
#   RENDER_SERVICE_ID  Target service id, e.g. srv-xxxxxxxxxxxx.
#   IMAGE              Full image reference incl. tag, e.g. ghcr.io/owner/app-backend:0.0.1
#
# Exit 0 only when the deploy reaches status "live". Any terminal failure (or timeout) exits non-zero;
# because Render is health-check gated, a failed deploy never receives traffic and the previous
# version stays live — so a red run here means "prod is untouched", not "prod is broken".
set -euo pipefail

: "${RENDER_API_KEY:?RENDER_API_KEY is required}"
: "${RENDER_SERVICE_ID:?RENDER_SERVICE_ID is required}"
: "${IMAGE:?IMAGE is required}"

API="https://api.render.com/v1"
AUTH="Authorization: Bearer ${RENDER_API_KEY}"
POLL_INTERVAL=15
TIMEOUT=1200 # 20 min

echo "Triggering Render deploy of ${IMAGE} on service ${RENDER_SERVICE_ID}..."
resp=$(curl -fsS -X POST "${API}/services/${RENDER_SERVICE_ID}/deploys" \
  -H "${AUTH}" -H "Content-Type: application/json" \
  -d "{\"imageUrl\":\"${IMAGE}\",\"clearCache\":\"do_not_clear\"}")

deploy_id=$(echo "$resp" | jq -r '.id // .deploy.id // empty')
if [ -z "$deploy_id" ]; then
  echo "::error::Could not create Render deploy. Response: $resp"
  exit 1
fi
echo "Render deploy created: ${deploy_id}"

deadline=$(( $(date +%s) + TIMEOUT ))
while true; do
  status=$(curl -fsS "${API}/services/${RENDER_SERVICE_ID}/deploys/${deploy_id}" \
    -H "${AUTH}" | jq -r '.status // .deploy.status // "unknown"')
  echo "  status: ${status}"
  case "$status" in
    live)
      echo "Deploy ${deploy_id} is live."
      exit 0
      ;;
    build_failed|update_failed|pre_deploy_failed|canceled|deactivated)
      echo "::error::Render deploy ${deploy_id} ended with status '${status}'. Previous version stays live (health-check gated)."
      exit 1
      ;;
  esac
  if [ "$(date +%s)" -ge "$deadline" ]; then
    echo "::error::Timed out after ${TIMEOUT}s waiting for deploy ${deploy_id} to go live (last status: ${status})."
    exit 1
  fi
  sleep "$POLL_INTERVAL"
done
