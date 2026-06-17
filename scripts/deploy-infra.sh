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
SCRIM_MANAGE_CLOUD_FUNCTIONS_IAM="${SCRIM_MANAGE_CLOUD_FUNCTIONS_IAM:-true}"
SCRIM_CLOUD_FUNCTIONS_DEPLOYER_MEMBER="${SCRIM_CLOUD_FUNCTIONS_DEPLOYER_MEMBER:-}"
SCRIM_SECRET_NAME_PREFIX="${SCRIM_SECRET_NAME_PREFIX:-}"
SCRIM_SECRETS_SERVICE_ACCOUNT_ID="${SCRIM_SECRETS_SERVICE_ACCOUNT_ID:-secrets-service-account}"

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
    -var="secret_name_prefix=${SCRIM_SECRET_NAME_PREFIX}"
    -var="secrets_service_account_id=${SCRIM_SECRETS_SERVICE_ACCOUNT_ID}"
    -var="manage_cloud_functions_iam=${SCRIM_MANAGE_CLOUD_FUNCTIONS_IAM}"
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

wait_for_secret_manager_create_permission() {
    local probe_secret="${SCRIM_SECRET_NAME_PREFIX}sm-permission-probe-${SCRIM_GITHUB_RUN_ID:-manual}"

    for attempt in $(seq 1 18); do
        if gcloud secrets create "$probe_secret" \
            --project="${SCRIM_PROJECT_ID}" \
            --replication-policy=automatic >/dev/null 2>&1; then
            gcloud secrets delete "$probe_secret" --project="${SCRIM_PROJECT_ID}" --quiet >/dev/null 2>&1 || true
            return 0
        fi

        echo "waiting for Secret Manager create permission to propagate (${attempt}/18)..."
        sleep 10
    done

    echo "error: Secret Manager create permission did not become available in time."
    return 1
}

ensure_secret() {
    local secret_name="$1"
    local secret_value="$2"

    if gcloud secrets describe "$secret_name" --project="${SCRIM_PROJECT_ID}" >/dev/null 2>&1; then
        printf '%s' "$secret_value" | gcloud secrets versions add "$secret_name" \
            --project="${SCRIM_PROJECT_ID}" \
            --data-file=- >/dev/null
    else
        printf '%s' "$secret_value" | gcloud secrets create "$secret_name" \
            --project="${SCRIM_PROJECT_ID}" \
            --replication-policy=automatic \
            --data-file=- >/dev/null
    fi
}

ensure_secret_manager_runtime_access() {
    if [ "${SCRIM_MANAGE_SECRET_MANAGER}" != "true" ]; then
        return 0
    fi

    local sa_email="${SCRIM_SECRETS_SERVICE_ACCOUNT_ID}@${SCRIM_PROJECT_ID}.iam.gserviceaccount.com"
    local workload_identity_member="serviceAccount:${SCRIM_PROJECT_ID}.svc.id.goog[${SCRIM_NAMESPACE}/scrimfinder-secrets-reader]"

    if ! gcloud iam service-accounts describe "$sa_email" --project="${SCRIM_PROJECT_ID}" >/dev/null 2>&1; then
        gcloud iam service-accounts create "$SCRIM_SECRETS_SERVICE_ACCOUNT_ID" \
            --project="${SCRIM_PROJECT_ID}" \
            --display-name="$SCRIM_SECRETS_SERVICE_ACCOUNT_ID" \
            --description="Service account with access to ScrimFinder secrets" >/dev/null
    fi

    gcloud iam service-accounts add-iam-policy-binding "$sa_email" \
        --project="${SCRIM_PROJECT_ID}" \
        --role=roles/iam.workloadIdentityUser \
        --member="$workload_identity_member" \
        --quiet >/dev/null
    gcloud iam service-accounts add-iam-policy-binding "$sa_email" \
        --project="${SCRIM_PROJECT_ID}" \
        --role=roles/iam.serviceAccountTokenCreator \
        --member="$workload_identity_member" \
        --quiet >/dev/null
    gcloud projects add-iam-policy-binding "${SCRIM_PROJECT_ID}" \
        --role=roles/secretmanager.secretAccessor \
        --member="serviceAccount:${sa_email}" \
        --quiet >/dev/null

    ensure_secret "${SCRIM_SECRET_NAME_PREFIX}riot-api-key" "$RIOT_API_KEY"
    ensure_secret "${SCRIM_SECRET_NAME_PREFIX}db-user" "$SCRIM_DB_USER"
    ensure_secret "${SCRIM_SECRET_NAME_PREFIX}db-password" "$SCRIM_DB_PASSWORD"
    ensure_secret "${SCRIM_SECRET_NAME_PREFIX}redis-password" "$SCRIM_REDIS_PASSWORD"
    ensure_secret "${SCRIM_SECRET_NAME_PREFIX}rabbitmq-user" "$SCRIM_RABBITMQ_USER"
    ensure_secret "${SCRIM_SECRET_NAME_PREFIX}rabbitmq-password" "$SCRIM_RABBITMQ_PASSWORD"
    ensure_secret "${SCRIM_SECRET_NAME_PREFIX}rabbitmq-erlang-cookie" "$SCRIM_RABBITMQ_ERLANG_COOKIE"
}

if [ "${SCRIM_MANAGE_SECRET_MANAGER}" = "true" ] && \
   [ "${SCRIM_MANAGE_CLOUD_FUNCTIONS_IAM}" = "true" ] && \
   [ -n "${SCRIM_CLOUD_FUNCTIONS_DEPLOYER_MEMBER}" ]; then
    echo "bootstrapping GCP services and IAM before managing resources..."
    terraform apply -input=false -auto-approve \
        -target=google_project_service.required \
        -target=google_project_iam_member.cloud_functions_deployer \
        "${TF_VAR_ARGS[@]}"
    wait_for_secret_manager_create_permission
    echo "waiting for IAM propagation..."
    sleep 45
fi

echo "importing pre-existing infrastructure resources when present..."
import_if_missing \
    "google_artifact_registry_repository.docker_repo[0]" \
    "projects/${SCRIM_PROJECT_ID}/locations/${SCRIM_REGION}/repositories/${SCRIM_REPO_NAME}"
import_if_missing \
    "google_artifact_registry_repository.docker_repo[0]" \
    "${SCRIM_PROJECT_ID}/${SCRIM_REGION}/${SCRIM_REPO_NAME}"

if ! terraform state show "google_artifact_registry_repository.docker_repo[0]" >/dev/null 2>&1; then
    if gcloud artifacts repositories describe "${SCRIM_REPO_NAME}" --location="${SCRIM_REGION}" --project="${SCRIM_PROJECT_ID}" >/dev/null 2>&1; then
        echo "artifact registry repository exists but could not be imported; skipping repo management in this apply."
        TF_VAR_ARGS=("${TF_VAR_ARGS[@]/-var=manage_artifact_registry_repository=true/-var=manage_artifact_registry_repository=false}")
    fi
fi

if [ "${SCRIM_MANAGE_SECRET_MANAGER}" = "true" ]; then
    SA_EMAIL="${SCRIM_SECRETS_SERVICE_ACCOUNT_ID}@${SCRIM_PROJECT_ID}.iam.gserviceaccount.com"
    import_if_missing \
        "google_service_account.secrets_sa[0]" \
        "projects/${SCRIM_PROJECT_ID}/serviceAccounts/${SA_EMAIL}"
fi

secret_manager_import_incomplete="false"
if [ "${SCRIM_MANAGE_SECRET_MANAGER}" = "true" ]; then
    if gcloud iam service-accounts describe "${SA_EMAIL}" --project="${SCRIM_PROJECT_ID}" >/dev/null 2>&1 && \
       ! terraform state show "google_service_account.secrets_sa[0]" >/dev/null 2>&1; then
        secret_manager_import_incomplete="true"
    fi
fi

for secret_name in \
    "${SCRIM_SECRET_NAME_PREFIX}riot-api-key" \
    "${SCRIM_SECRET_NAME_PREFIX}db-user" \
    "${SCRIM_SECRET_NAME_PREFIX}db-password" \
    "${SCRIM_SECRET_NAME_PREFIX}redis-password" \
    "${SCRIM_SECRET_NAME_PREFIX}rabbitmq-user" \
    "${SCRIM_SECRET_NAME_PREFIX}rabbitmq-password" \
    "${SCRIM_SECRET_NAME_PREFIX}rabbitmq-erlang-cookie"; do
    import_if_missing \
        "google_secret_manager_secret.scrim_secrets[\"${secret_name}\"]" \
        "projects/${SCRIM_PROJECT_ID}/secrets/${secret_name}"

    if [ "${SCRIM_MANAGE_SECRET_MANAGER}" = "true" ] && \
       gcloud secrets describe "${secret_name}" --project="${SCRIM_PROJECT_ID}" >/dev/null 2>&1 && \
       ! terraform state show "google_secret_manager_secret.scrim_secrets[\"${secret_name}\"]" >/dev/null 2>&1; then
        secret_manager_import_incomplete="true"
    fi
done

# If existing secret-manager resources cannot be imported, avoid 409 create conflicts in this run.
if [ "${SCRIM_MANAGE_SECRET_MANAGER}" = "true" ] && [ "${secret_manager_import_incomplete}" = "true" ]; then
    echo "secret manager resources exist but could not be imported; skipping secret-manager resource creation in this apply."
    TF_VAR_ARGS=("${TF_VAR_ARGS[@]/-var=manage_secret_manager=true/-var=manage_secret_manager=false}")
fi

terraform apply -input=false -auto-approve "${TF_VAR_ARGS[@]}"

echo "ensuring Secret Manager runtime access and values..."
ensure_secret_manager_runtime_access

echo "Terraform apply complete."
