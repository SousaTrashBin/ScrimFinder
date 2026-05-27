#!/usr/bin/env bash
set -euo pipefail

run_id="${GITHUB_RUN_ID:-local}"
run_attempt="${GITHUB_RUN_ATTEMPT:-1}"
sha="${GITHUB_SHA:-local}"
pr_number="${GITHUB_EVENT_PULL_REQUEST_NUMBER:-${PR_NUMBER:-}}"

if [ -z "$pr_number" ] && [ -n "${GITHUB_EVENT_PATH:-}" ] && [ -f "$GITHUB_EVENT_PATH" ]; then
  pr_number="$(sed -n 's/.*"number"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$GITHUB_EVENT_PATH" | head -n 1)"
fi

if [ -n "$pr_number" ]; then
  environment_name="pr-${pr_number}-${run_id}-${run_attempt}"
else
  environment_name="run-${run_id}-${run_attempt}"
fi

environment_name="$(printf '%s' "$environment_name" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9-]+/-/g; s/^-+//; s/-+$//')"
environment_name="${environment_name:0:42}"
image_tag="$(printf '%s-%s-%s' "$sha" "$run_id" "$run_attempt" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9_.-]+/-/g')"

export SCRIM_ENVIRONMENT_NAME="${SCRIM_ENVIRONMENT_NAME:-$environment_name}"
export SCRIM_CI_STACK_NAME="${SCRIM_CI_STACK_NAME_OVERRIDE:-$SCRIM_ENVIRONMENT_NAME}"
export SCRIM_CLUSTER_NAME="${SCRIM_CLUSTER_NAME_OVERRIDE:-scrimfinder-${SCRIM_ENVIRONMENT_NAME}}"
export SCRIM_NAMESPACE="${SCRIM_NAMESPACE_OVERRIDE:-scrimfinder-${SCRIM_ENVIRONMENT_NAME}}"
export SCRIM_IMAGE_TAG="${SCRIM_IMAGE_TAG:-$image_tag}"
export SCRIM_TF_WORKSPACE="${SCRIM_TF_WORKSPACE:-$SCRIM_ENVIRONMENT_NAME}"
export SCRIM_TF_STATE_KEY="${SCRIM_TF_STATE_KEY:-${SCRIM_ENVIRONMENT_NAME}/terraform.tfstate}"
export SCRIM_GITHUB_RUN_ID="${SCRIM_GITHUB_RUN_ID:-$run_id}"
export SCRIM_GITHUB_PR="${SCRIM_GITHUB_PR:-$pr_number}"
export SCRIM_MANAGE_SECRET_MANAGER="${SCRIM_MANAGE_SECRET_MANAGER:-false}"
export SCRIM_MANAGE_ARTIFACT_REGISTRY_REPOSITORY="${SCRIM_MANAGE_ARTIFACT_REGISTRY_REPOSITORY:-false}"

emit_env() {
  cat <<EOF
SCRIM_ENVIRONMENT_NAME=$SCRIM_ENVIRONMENT_NAME
SCRIM_CI_STACK_NAME=$SCRIM_CI_STACK_NAME
SCRIM_CLUSTER_NAME=$SCRIM_CLUSTER_NAME
SCRIM_NAMESPACE=$SCRIM_NAMESPACE
SCRIM_IMAGE_TAG=$SCRIM_IMAGE_TAG
SCRIM_TF_WORKSPACE=$SCRIM_TF_WORKSPACE
SCRIM_TF_STATE_KEY=$SCRIM_TF_STATE_KEY
SCRIM_GITHUB_RUN_ID=$SCRIM_GITHUB_RUN_ID
SCRIM_GITHUB_PR=$SCRIM_GITHUB_PR
SCRIM_MANAGE_SECRET_MANAGER=$SCRIM_MANAGE_SECRET_MANAGER
SCRIM_MANAGE_ARTIFACT_REGISTRY_REPOSITORY=$SCRIM_MANAGE_ARTIFACT_REGISTRY_REPOSITORY
EOF
}

if [ -n "${GITHUB_ENV:-}" ]; then
  emit_env >> "$GITHUB_ENV"
fi

emit_env
