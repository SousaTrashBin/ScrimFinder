#!/bin/bash
set -euo pipefail

REQUIRED_VARS="SCRIM_PROJECT_ID SCRIM_REGION SCRIM_REPO_NAME SCRIM_CLUSTER_NAME RIOT_API_KEY SCRIM_DB_USER SCRIM_DB_PASSWORD"

for var in $REQUIRED_VARS; do
    if [ -z "${!var:-}" ]; then
        echo "error: $var is not set. please set it in your system environment."
        exit 1
    fi
done

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TF_DIR="${ROOT_DIR}/infrastructure/terraform"

SCRIM_NAMESPACE="${SCRIM_NAMESPACE:-scrimfinder}"
SCRIM_REDIS_PASSWORD="${SCRIM_REDIS_PASSWORD:-redispassword}"
SCRIM_RABBITMQ_USER="${SCRIM_RABBITMQ_USER:-user}"
SCRIM_RABBITMQ_PASSWORD="${SCRIM_RABBITMQ_PASSWORD:-rabbitmqpassword}"
SCRIM_RABBITMQ_ERLANG_COOKIE="${SCRIM_RABBITMQ_ERLANG_COOKIE:-erlangcookie}"
SCRIM_TF_WORKSPACE="${SCRIM_TF_WORKSPACE:-default}"
SCRIM_ENVIRONMENT_NAME="${SCRIM_ENVIRONMENT_NAME:-manual}"
SCRIM_GITHUB_RUN_ID="${SCRIM_GITHUB_RUN_ID:-${GITHUB_RUN_ID:-}}"
SCRIM_GITHUB_PR="${SCRIM_GITHUB_PR:-${PR_NUMBER:-}}"
SCRIM_TF_STATE_KEY="${SCRIM_TF_STATE_KEY:-${SCRIM_TF_WORKSPACE}/terraform.tfstate}"
SCRIM_MANAGE_SECRET_MANAGER="${SCRIM_MANAGE_SECRET_MANAGER:-true}"
SCRIM_MANAGE_ARTIFACT_REGISTRY_REPOSITORY="${SCRIM_MANAGE_ARTIFACT_REGISTRY_REPOSITORY:-true}"
SCRIM_CLOUD_FUNCTIONS_DEPLOYER_MEMBER="${SCRIM_CLOUD_FUNCTIONS_DEPLOYER_MEMBER:-"serviceAccount:${SCRIM_PROJECT_ID}-compute@developer.gserviceaccount.com"}"

echo "applying Terraform infrastructure..."
gcloud config set project "${SCRIM_PROJECT_ID}" --quiet
cd "${TF_DIR}"
if [ -n "${TF_STATE_BUCKET:-}" ]; then
    cat > backend-ci.tf <<EOF
terraform {
  backend "gcs" {}
}
EOF
    terraform init -input=false \
        -backend-config="bucket=${TF_STATE_BUCKET}" \
        -backend-config="prefix=${SCRIM_TF_STATE_KEY}"
else
    terraform init -input=false
    terraform workspace select "${SCRIM_TF_WORKSPACE}" >/dev/null 2>&1 || terraform workspace new "${SCRIM_TF_WORKSPACE}"
fi

TF_VAR_ARGS=(
    -var="project_id=${SCRIM_PROJECT_ID}"
    -var="region=${SCRIM_REGION}"
    -var="repo_name=${SCRIM_REPO_NAME}"
    -var="cluster_name=${SCRIM_CLUSTER_NAME}"
    -var="namespace=${SCRIM_NAMESPACE}"
    -var="riot_api_key=${RIOT_API_KEY}"
    -var="db_user=${SCRIM_DB_USER}"
    -var="db_password=${SCRIM_DB_PASSWORD}"
    -var="redis_password=${SCRIM_REDIS_PASSWORD}"
    -var="rabbitmq_user=${SCRIM_RABBITMQ_USER}"
    -var="rabbitmq_password=${SCRIM_RABBITMQ_PASSWORD}"
    -var="rabbitmq_erlang_cookie=${SCRIM_RABBITMQ_ERLANG_COOKIE}"
    -var="manage_artifact_registry_repository=${SCRIM_MANAGE_ARTIFACT_REGISTRY_REPOSITORY}"
    -var="manage_secret_manager=${SCRIM_MANAGE_SECRET_MANAGER}"
    -var="environment_name=${SCRIM_ENVIRONMENT_NAME}"
    -var="github_run_id=${SCRIM_GITHUB_RUN_ID}"
    -var="github_pr=${SCRIM_GITHUB_PR}"
    -var="cloud_functions_deployer_member=${SCRIM_CLOUD_FUNCTIONS_DEPLOYER_MEMBER}"
)

import_if_missing() {
    local address="$1"
    local import_id="$2"

    if terraform state show "$address" >/dev/null 2>&1; then
        return 0
    fi

    terraform import -input=false "${TF_VAR_ARGS[@]}" "$address" "$import_id" >/dev/null 2>&1 || true
}

echo "importing pre-existing infrastructure resources when present..."
import_if_missing \
    "google_artifact_registry_repository.docker_repo[0]" \
    "projects/${SCRIM_PROJECT_ID}/locations/${SCRIM_REGION}/repositories/${SCRIM_REPO_NAME}"
import_if_missing \
    "google_artifact_registry_repository.docker_repo[0]" \
    "${SCRIM_PROJECT_ID}/${SCRIM_REGION}/${SCRIM_REPO_NAME}"

if ! terraform state show google_artifact_registry_repository.docker_repo[0] >/dev/null 2>&1; then
    if gcloud artifacts repositories describe "${SCRIM_REPO_NAME}" --location="${SCRIM_REGION}" --project="${SCRIM_PROJECT_ID}" >/dev/null 2>&1; then
        echo "artifact registry repository exists but could not be imported; skipping repo management in this apply."
        TF_VAR_ARGS=("${TF_VAR_ARGS[@]/-var=manage_artifact_registry_repository=true/-var=manage_artifact_registry_repository=false}")
    fi
fi

for secret_name in \
    "RIOT_API_KEY" \
    "riot-api-key" \
    "db-user" \
    "db-password" \
    "redis-password" \
    "rabbitmq-user" \
    "rabbitmq-password" \
    "rabbitmq-erlang-cookie"; do
    import_if_missing \
        "google_secret_manager_secret.scrim_secrets[\"${secret_name}\"]" \
        "projects/${SCRIM_PROJECT_ID}/secrets/${secret_name}"
done

terraform apply -input=false -auto-approve "${TF_VAR_ARGS[@]}"

echo "Terraform apply complete."
