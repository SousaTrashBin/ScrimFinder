#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

if [ -z "${SCRIM_CLUSTER_NAME:-}" ] || [ -z "${SCRIM_TF_STATE_KEY:-}" ]; then
  # shellcheck source=/dev/null
  source "$ROOT_DIR/scripts/ci/derive-env.sh" >/dev/null
fi

required_vars=(
  SCRIM_PROJECT_ID SCRIM_REGION SCRIM_REPO_NAME SCRIM_CLUSTER_NAME SCRIM_NAMESPACE
  RIOT_API_KEY SCRIM_DB_USER SCRIM_DB_PASSWORD
)

for var in "${required_vars[@]}"; do
  if [ -z "${!var:-}" ]; then
    echo "error: $var is not set"
    exit 1
  fi
done

SCRIM_REDIS_PASSWORD="${SCRIM_REDIS_PASSWORD:-redispassword}"
SCRIM_RABBITMQ_USER="${SCRIM_RABBITMQ_USER:-user}"
SCRIM_RABBITMQ_PASSWORD="${SCRIM_RABBITMQ_PASSWORD:-rabbitmqpassword}"
SCRIM_RABBITMQ_ERLANG_COOKIE="${SCRIM_RABBITMQ_ERLANG_COOKIE:-erlangcookie}"
SCRIM_RABBITMQ_HOST="${SCRIM_RABBITMQ_HOST:-scrimfinder-rabbitmq-broker}"
SCRIM_RABBITMQ_PORT="${SCRIM_RABBITMQ_PORT:-5672}"

export SCRIM_MANAGE_SECRET_MANAGER="${SCRIM_MANAGE_SECRET_MANAGER:-true}"
export SCRIM_MANAGE_ARTIFACT_REGISTRY_REPOSITORY="${SCRIM_MANAGE_ARTIFACT_REGISTRY_REPOSITORY:-false}"
export SCRIM_MANAGE_CLOUD_FUNCTIONS_IAM="${SCRIM_MANAGE_CLOUD_FUNCTIONS_IAM:-true}"

"$ROOT_DIR/scripts/deploy-infra.sh"
