#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

if [ -z "${SCRIM_PROJECT_ID:-}" ] || [ -z "${SCRIM_REGION:-}" ] || [ -z "${SCRIM_CLUSTER_NAME:-}" ]; then
  echo "missing GKE teardown environment; nothing to destroy"
  exit 0
fi

export SCRIM_REPO_NAME="${SCRIM_REPO_NAME:-scrimfinder}"
export RIOT_API_KEY="${RIOT_API_KEY:-teardown-placeholder}"
export SCRIM_DB_USER="${SCRIM_DB_USER:-postgres}"
export SCRIM_DB_PASSWORD="${SCRIM_DB_PASSWORD:-teardown-placeholder}"
export SCRIM_MANAGE_SECRET_MANAGER="${SCRIM_MANAGE_SECRET_MANAGER:-false}"
export SCRIM_MANAGE_ARTIFACT_REGISTRY_REPOSITORY="${SCRIM_MANAGE_ARTIFACT_REGISTRY_REPOSITORY:-false}"
export SCRIM_NAMESPACE="${SCRIM_NAMESPACE:-scrimfinder}"

"$ROOT_DIR/scripts/shutdown.sh"
