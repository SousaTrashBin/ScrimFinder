#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

: "${SCRIM_PROJECT_ID:?}"
: "${SCRIM_REGION:?}"
: "${SCRIM_REPO_NAME:?}"
: "${SCRIM_IMAGE_TAG:?}"

registry="${SCRIM_REGION}-docker.pkg.dev/${SCRIM_PROJECT_ID}/${SCRIM_REPO_NAME}"
services="${SERVICES:-matchmaking-service ranking_service match_history_service detail_filling_service training_service analysis_service}"

gcloud auth configure-docker "${SCRIM_REGION}-docker.pkg.dev" --quiet

for service in $services; do
  (
    image_name="$(printf '%s' "$service" | tr '_' '-')"
    image_path="${registry}/${image_name}:${SCRIM_IMAGE_TAG}"
    docker buildx build --platform linux/amd64 -t "$image_path" "$ROOT_DIR/$service" --push --quiet
  ) &
done
wait
