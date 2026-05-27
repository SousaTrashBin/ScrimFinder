#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=lib/cloud-functions.sh
source "$ROOT_DIR/scripts/lib/cloud-functions.sh"

REQUIRED_VARS="SCRIM_PROJECT_ID SCRIM_REGION SCRIM_REPO_NAME SCRIM_CLUSTER_NAME RIOT_API_KEY SCRIM_DB_USER SCRIM_DB_PASSWORD"

for var in $REQUIRED_VARS; do
    if [ -z "${!var:-}" ]; then
        echo "error: $var is not set. please set it in your system environment."
        exit 1
    fi
done

PROJECT_ID="$SCRIM_PROJECT_ID"
REGION="$SCRIM_REGION"
REPO_NAME="$SCRIM_REPO_NAME"
CLUSTER_NAME="$SCRIM_CLUSTER_NAME"

SCRIM_NAMESPACE="${SCRIM_NAMESPACE:-scrimfinder}"

SCRIM_RABBITMQ_HOST="${SCRIM_RABBITMQ_HOST:-scrimfinder-rabbitmq-broker}"
SCRIM_RABBITMQ_PORT="${SCRIM_RABBITMQ_PORT:-5672}"
SCRIM_IMAGE_TAG="${SCRIM_IMAGE_TAG:-latest}"

echo "checking GCP configuration..."
gcloud config set project "$PROJECT_ID" --quiet

echo "provisioning infrastructure with Terraform..."
"$ROOT_DIR/scripts/deploy-infra.sh"

FUNCTIONS_BUILD_SERVICE_ACCOUNT="$(functions_build_service_account "$PROJECT_ID")"
SECRETS_SERVICE_ACCOUNT_EMAIL="secrets-service-account@${PROJECT_ID}.iam.gserviceaccount.com"

ZONE="${REGION}-a"
echo "fetching GKE credentials..."
gcloud container clusters get-credentials "$CLUSTER_NAME" \
    --zone "$ZONE" \
    --project "$PROJECT_ID"

echo "authenticating Docker to Artifact Registry..."
gcloud auth configure-docker "$REGION-docker.pkg.dev" --quiet

if [ -z "${SERVICES:-}" ]; then
    SERVICES="matchmaking-service ranking_service match_history_service training_service analysis_service"
fi

if [ "${SKIP_BUILD:-false}" != "true" ]; then
    echo "building and pushing services in parallel..."

    for SERVICE in $SERVICES; do
        (
            IMAGE_NAME=$(echo "$SERVICE" | tr '_' '-')
            IMAGE_PATH="$REGION-docker.pkg.dev/$PROJECT_ID/$REPO_NAME/$IMAGE_NAME:$SCRIM_IMAGE_TAG"

            echo "building $SERVICE..."
            docker buildx build \
                --platform linux/amd64 \
                -t "$IMAGE_PATH" \
                "$ROOT_DIR/$SERVICE" \
                --push \
                --quiet

            echo "finished $SERVICE."
        ) &
    done

    wait
    echo "all builds complete."
else
    echo "skipping build phase."
fi

ensure_riot_api_secret "$PROJECT_ID" "$RIOT_API_KEY"
package_detail_filling_function_source "$ROOT_DIR" true clean package
deploy_detail_filling_functions "$ROOT_DIR" "$REGION" "$FUNCTIONS_BUILD_SERVICE_ACCOUNT" "$SECRETS_SERVICE_ACCOUNT_EMAIL"
echo "all serverless functions done deploying."
discover_detail_filling_domain "$REGION"

echo "installing VPA CRDs..."
git clone https://github.com/kubernetes/autoscaler.git || true
cp -f ./scripts/gencerts.sh ./autoscaler/vertical-pod-autoscaler/pkg/admission-controller/
cd autoscaler/vertical-pod-autoscaler
./hack/vpa-up.sh
cd ../..
rm -r autoscaler &
wait

echo "updating Helm dependencies..."
helm dependency update k8s/charts/scrimfinder

echo "deploying application with local Helm chart..."
helm upgrade --install scrimfinder "$ROOT_DIR/k8s/charts/scrimfinder" \
    --namespace "$SCRIM_NAMESPACE" \
    --create-namespace \
    --wait \
    --timeout 25m \
    --set global.namespace="${SCRIM_NAMESPACE}" \
    --set global.projectID="${PROJECT_ID}" \
    --set global.microservicesRegistry="${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO_NAME}" \
    --set global.imageTag="${SCRIM_IMAGE_TAG}" \
    --set global.region="${REGION}" \
    --set global.projectId="${PROJECT_ID}" \
    --set global.repoName="${REPO_NAME}" \
    --set global.rabbitmqHost="${SCRIM_RABBITMQ_HOST}" \
    --set global.rabbitmqPort="${SCRIM_RABBITMQ_PORT}" \
    --set global.useSecretManager=true \
    --set global.useArgoApplications=true \
    --set global.useVerticalPodAutoscaler=true \
    --set detailFillingExternal.enabled=true \
    --set detailFillingExternal.externalName="${DETAIL_FILLING_DOMAIN}" \
    --set services.detail-filling-service.enabled=false \
    --set services.ranking-service.env.DETAIL_FILLING_SERVICE_URL="http://scrimfinder-traefik/api/v1/riot" \
    --set services.match-history-service.env.PLAYER_FILLING_SVC_URL="http://scrimfinder-traefik/api/v1/riot" \
    --set services.training-service.env.DETAIL_FILLING_URL="http://scrimfinder-traefik/api/v1/riot"#

echo "deploying Argo CD..."

export SCRIM_NAMESPACE="${SCRIM_NAMESPACE}"
export PROJECT_ID="${PROJECT_ID}"
export REGION="${REGION}"
export REPO_NAME="${REPO_NAME}"
export SCRIM_RABBITMQ_HOST="${SCRIM_RABBITMQ_HOST}"
export SCRIM_RABBITMQ_PORT="${SCRIM_RABBITMQ_PORT}"
export DETAIL_FILLING_DOMAIN="${DETAIL_FILLING_DOMAIN}"

kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -n argocd --server-side --force-conflicts -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl patch svc argocd-server -n argocd -p '{"spec": {"type": "LoadBalancer"}}'

if command -v envsubst >/dev/null 2>&1; then
    envsubst < k8s/application.yaml | kubectl apply -n argocd --server-side --force-conflicts -f -
else
    echo "warning: envsubst not found. applying manifests without variable substitution..."
    kubectl apply -n argocd --server-side --force-conflicts -f k8s/application.yaml
fi

echo "waiting for Argo CD LoadBalancer External IP/Hostname..."

EXTERNAL_ARGOCD_IP=""

while [ -z "$EXTERNAL_ARGOCD_IP" ]; do
    echo "waiting for argocd IP..."

    EXTERNAL_ARGOCD_IP=$(kubectl get svc argocd-server \
        -n argocd \
        -o=jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")

    if [ -z "$EXTERNAL_ARGOCD_IP" ]; then
        echo "checking for Hostname..."

        EXTERNAL_ARGOCD_IP=$(kubectl get svc argocd-server \
            -n argocd \
            -o=jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
    fi

    [ -z "$EXTERNAL_ARGOCD_IP" ] && sleep 10
done

INITIAL_ARGOCD_PASSWORD=$(kubectl -n argocd get secret argocd-initial-admin-secret \
    -o jsonpath="{.data.password}" | base64 -d)

echo "Argo CD External IP/Hostname: ${EXTERNAL_ARGOCD_IP}; username: admin; initial password: $INITIAL_ARGOCD_PASSWORD"

echo "waiting for Traefik LoadBalancer External IP/Hostname..."
echo "this might take a few minutes..."

EXTERNAL_IP=""

while [ -z "$EXTERNAL_IP" ]; do
    echo "waiting for IP..."

    EXTERNAL_IP=$(kubectl get svc scrimfinder-traefik \
        -n "$SCRIM_NAMESPACE" \
        -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")

    if [ -z "$EXTERNAL_IP" ]; then
        echo "checking for Hostname..."

        EXTERNAL_IP=$(kubectl get svc scrimfinder-traefik \
            -n "$SCRIM_NAMESPACE" \
            -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "")
    fi

    [ -z "$EXTERNAL_IP" ] && sleep 10
done

echo "Traefik External IP/Hostname: $EXTERNAL_IP"
echo "deployment complete!"
