#!/bin/bash
set -euo pipefail

REQUIRED_VARS="SCRIM_PROJECT_ID SCRIM_REGION SCRIM_REPO_NAME SCRIM_CLUSTER_NAME RIOT_API_KEY SCRIM_DB_USER SCRIM_DB_PASSWORD"

for var in $REQUIRED_VARS; do
    if [ -z "${!var:-}" ]; then
        echo "error: $var is not set. please set it in your system environment."
        exit 1
    fi
done

PROJECT_ID="$SCRIM_PROJECT_ID"
REGION="$SCRIM_REGION"
CLUSTER_NAME="$SCRIM_CLUSTER_NAME"
SCRIM_NAMESPACE="${SCRIM_NAMESPACE:-scrimfinder}"
ZONE="${REGION}-a"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TF_DIR="${ROOT_DIR}/infrastructure/terraform"

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
SCRIM_FORCE_SECRET_MANAGER_CLEANUP="${SCRIM_FORCE_SECRET_MANAGER_CLEANUP:-false}"
SCRIM_SECRET_NAME_PREFIX="${SCRIM_SECRET_NAME_PREFIX:-}"
SCRIM_SECRETS_SERVICE_ACCOUNT_ID="${SCRIM_SECRETS_SERVICE_ACCOUNT_ID:-secrets-service-account}"

DELETE_ARTIFACT_REPO="${SCRIM_DELETE_ARTIFACT_REPO:-false}"
DELETE_UNUSED_K8S_IPS="${SCRIM_DELETE_UNUSED_K8S_IPS:-true}"
DELETE_ORPHAN_PVC_DISKS="${SCRIM_DELETE_ORPHAN_PVC_DISKS:-true}"

echo "setting active GCP project to $PROJECT_ID..."
gcloud config set project "$PROJECT_ID" --quiet

cleanup_resources() {
    echo "deleting Argo CD resources..."
    if kubectl get crd applications.argoproj.io >/dev/null 2>&1; then
        kubectl -n argocd patch application.argoproj.io scrimfinder -p '{"metadata": {"finalizers": ["resources-finalizer.argocd.argoproj.io"]}}' --type merge || true
        kubectl -n argocd delete application.argoproj.io scrimfinder --wait=true --timeout=3m || true
    fi
    kubectl delete -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml --wait=true --timeout=1m || true
    kubectl delete namespace argocd --ignore-not-found=true --wait=true --timeout=1m || true
}

if gcloud container clusters describe "$CLUSTER_NAME" --zone "$ZONE" --project "$PROJECT_ID" >/dev/null 2>&1; then
    echo "fetching GKE credentials..."
    gcloud container clusters get-credentials "$CLUSTER_NAME" --zone "$ZONE" --project "$PROJECT_ID"

    cleanup_resources

else
    echo "cluster $CLUSTER_NAME not found or already deleted."
fi

if [ "${SKIP_CLUSTER_SHUTDOWN:-false}" = "true" ]; then
    echo "skipping Terraform destroy (SKIP_CLUSTER_SHUTDOWN=true)."
else
    echo "destroying infrastructure with Terraform..."
    if [ -n "${TF_STATE_BUCKET:-}" ]; then
        cat > "${TF_DIR}/backend-ci.tf" <<EOF
terraform {
  backend "gcs" {}
}
EOF
        terraform -chdir="$TF_DIR" init -input=false \
            -backend-config="bucket=${TF_STATE_BUCKET}" \
            -backend-config="prefix=${SCRIM_TF_STATE_KEY}"
    else
        terraform -chdir="$TF_DIR" init -input=false
        terraform -chdir="$TF_DIR" workspace select "${SCRIM_TF_WORKSPACE}" >/dev/null 2>&1 || terraform -chdir="$TF_DIR" workspace new "${SCRIM_TF_WORKSPACE}"
    fi
    # Avoid API-disable failures on destroy; keep enabled services unmanaged at teardown time.
    PROJECT_SERVICE_STATE="$(terraform -chdir="$TF_DIR" state list | grep '^google_project_service\.required' || true)"
    if [ -n "$PROJECT_SERVICE_STATE" ]; then
        while IFS= read -r state_addr; do
            [ -z "$state_addr" ] && continue
            terraform -chdir="$TF_DIR" state rm "$state_addr" >/dev/null || true
        done <<EOF
$PROJECT_SERVICE_STATE
EOF
    fi
    terraform -chdir="$TF_DIR" destroy -input=false -auto-approve \
        -var="project_id=${SCRIM_PROJECT_ID}" \
        -var="region=${SCRIM_REGION}" \
        -var="repo_name=${SCRIM_REPO_NAME}" \
        -var="cluster_name=${SCRIM_CLUSTER_NAME}" \
        -var="namespace=${SCRIM_NAMESPACE}" \
        -var="riot_api_key=${RIOT_API_KEY}" \
        -var="db_user=${SCRIM_DB_USER}" \
        -var="db_password=${SCRIM_DB_PASSWORD}" \
        -var="redis_password=${SCRIM_REDIS_PASSWORD}" \
        -var="rabbitmq_user=${SCRIM_RABBITMQ_USER}" \
        -var="rabbitmq_password=${SCRIM_RABBITMQ_PASSWORD}" \
        -var="rabbitmq_erlang_cookie=${SCRIM_RABBITMQ_ERLANG_COOKIE}" \
        -var="manage_artifact_registry_repository=${SCRIM_MANAGE_ARTIFACT_REGISTRY_REPOSITORY}" \
        -var="manage_secret_manager=${SCRIM_MANAGE_SECRET_MANAGER}" \
        -var="secret_name_prefix=${SCRIM_SECRET_NAME_PREFIX}" \
        -var="secrets_service_account_id=${SCRIM_SECRETS_SERVICE_ACCOUNT_ID}" \
        -var="manage_cloud_functions_iam=${SCRIM_MANAGE_CLOUD_FUNCTIONS_IAM}" \
        -var="environment_name=${SCRIM_ENVIRONMENT_NAME}" \
        -var="github_run_id=${SCRIM_GITHUB_RUN_ID}" \
        -var="github_pr=${SCRIM_GITHUB_PR}" \
        -var="cloud_functions_deployer_member=${SCRIM_CLOUD_FUNCTIONS_DEPLOYER_MEMBER}"
fi

if [ "$DELETE_UNUSED_K8S_IPS" = "true" ]; then
    echo "cleaning unused regional static IPs likely created by Kubernetes load balancers..."
    CANDIDATE_IPS="$(gcloud compute addresses list \
        --project "$PROJECT_ID" \
        --filter="region:($REGION) AND status=RESERVED" \
        --format="csv[no-heading](name,users)" | awk -F',' '
            $2 == "" && $1 ~ /^(k8s-|scrimfinder|traefik)/ { print $1 }
        ' || true)"

    if [ -n "${SCRIM_ENVIRONMENT_NAME:-}" ] && [ "$SCRIM_ENVIRONMENT_NAME" != "manual" ]; then
        ENVIRONMENT_IPS="$(gcloud compute addresses list \
            --project "$PROJECT_ID" \
            --filter="region:($REGION) AND status=RESERVED AND labels.environment=${SCRIM_ENVIRONMENT_NAME}" \
            --format="value(name)" || true)"
        CANDIDATE_IPS="$(printf '%s\n%s\n' "$CANDIDATE_IPS" "$ENVIRONMENT_IPS" | awk 'NF && !seen[$0]++')"
    fi

    if [ -n "$CANDIDATE_IPS" ]; then
        while IFS= read -r ip_name; do
            [ -z "$ip_name" ] && continue
            echo "deleting unused IP: $ip_name"
            gcloud compute addresses delete "$ip_name" --region "$REGION" --project "$PROJECT_ID" --quiet || true
        done <<EOF
$CANDIDATE_IPS
EOF
    else
        echo "no obvious unused Kubernetes-related static IPs found in $REGION."
    fi
fi

if [ "$DELETE_ORPHAN_PVC_DISKS" = "true" ]; then
    echo "cleaning orphan PersistentVolume disks..."

    if [ -n "${SCRIM_ENVIRONMENT_NAME:-}" ] && [ "$SCRIM_ENVIRONMENT_NAME" != "manual" ]; then
        ENVIRONMENT_DISKS="$(gcloud compute disks list \
            --project "$PROJECT_ID" \
            --filter="labels.environment=${SCRIM_ENVIRONMENT_NAME}" \
            --format="value(name,zone)" || true)"
        
        if [ -n "$ENVIRONMENT_DISKS" ]; then
            echo "Found disks labeled with environment ${SCRIM_ENVIRONMENT_NAME}. Deleting..."
            while read -r disk_name disk_zone; do
                [ -z "$disk_name" ] && continue
                echo "deleting labeled disk: $disk_name ($disk_zone)"
                gcloud compute disks delete "$disk_name" --zone "$disk_zone" --project "$PROJECT_ID" --quiet || true
            done <<< "$ENVIRONMENT_DISKS"
        fi
    fi

    ORPHAN_PVC_DISKS="$(gcloud compute disks list \
        --project "$PROJECT_ID" \
        --filter="name~'^pvc-' AND users:*" \
        --format="csv[no-heading](name,zone,users)" | awk -F',' '$3 == "" { print $1,$2 }' || true)"

    if [ -n "$ORPHAN_PVC_DISKS" ]; then
        echo "Found orphan PVC disks with no users. Deleting..."
        while read -r disk_name disk_zone; do
            [ -z "$disk_name" ] && continue
            echo "deleting orphan disk: $disk_name ($disk_zone)"
            gcloud compute disks delete "$disk_name" --zone "$disk_zone" --project "$PROJECT_ID" --quiet || true
        done <<< "$ORPHAN_PVC_DISKS"
    fi
fi

if [ "$DELETE_ARTIFACT_REPO" = "true" ]; then
    echo "SCRIM_DELETE_ARTIFACT_REPO=true is set, but Artifact Registry is managed by Terraform and will be destroyed with the stack."
fi

if [ "${SCRIM_FORCE_SECRET_MANAGER_CLEANUP}" = "true" ]; then
    echo "force-cleaning secret manager resources..."
    if [ -z "${SCRIM_SECRET_NAME_PREFIX}" ]; then
        gcloud secrets delete "RIOT_API_KEY" --project "$PROJECT_ID" --quiet >/dev/null 2>&1 || true
    fi

    for secret_name in \
        "${SCRIM_SECRET_NAME_PREFIX}riot-api-key" \
        "${SCRIM_SECRET_NAME_PREFIX}db-user" \
        "${SCRIM_SECRET_NAME_PREFIX}db-password" \
        "${SCRIM_SECRET_NAME_PREFIX}redis-password" \
        "${SCRIM_SECRET_NAME_PREFIX}rabbitmq-user" \
        "${SCRIM_SECRET_NAME_PREFIX}rabbitmq-password" \
        "${SCRIM_SECRET_NAME_PREFIX}rabbitmq-erlang-cookie"; do
        gcloud secrets delete "$secret_name" --project "$PROJECT_ID" --quiet >/dev/null 2>&1 || true
    done
    gcloud iam service-accounts delete \
        "${SCRIM_SECRETS_SERVICE_ACCOUNT_ID}@${PROJECT_ID}.iam.gserviceaccount.com" \
        --project "$PROJECT_ID" \
        --quiet >/dev/null 2>&1 || true
fi

echo "shutdown complete!"
