#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# shellcheck source=../lib/cloud-functions.sh
source "$ROOT_DIR/scripts/lib/cloud-functions.sh"

: "${SCRIM_PROJECT_ID:?}"
: "${SCRIM_REGION:?}"
: "${SCRIM_REPO_NAME:?}"
: "${SCRIM_CLUSTER_NAME:?}"
: "${SCRIM_NAMESPACE:?}"
: "${SCRIM_IMAGE_TAG:?}"
: "${RIOT_API_KEY:?}"
: "${SCRIM_DB_USER:?}"
: "${SCRIM_DB_PASSWORD:?}"

SCRIM_REDIS_PASSWORD="${SCRIM_REDIS_PASSWORD:-redispassword}"
SCRIM_RABBITMQ_USER="${SCRIM_RABBITMQ_USER:-user}"
SCRIM_RABBITMQ_PASSWORD="${SCRIM_RABBITMQ_PASSWORD:-rabbitmqpassword}"
SCRIM_RABBITMQ_ERLANG_COOKIE="${SCRIM_RABBITMQ_ERLANG_COOKIE:-erlangcookie}"
SCRIM_RABBITMQ_HOST="${SCRIM_RABBITMQ_HOST:-scrimfinder-rabbitmq-broker}"
SCRIM_RABBITMQ_PORT="${SCRIM_RABBITMQ_PORT:-5672}"

zone="${SCRIM_REGION}-a"
registry="${SCRIM_REGION}-docker.pkg.dev/${SCRIM_PROJECT_ID}/${SCRIM_REPO_NAME}"

gcloud container clusters get-credentials "$SCRIM_CLUSTER_NAME" --zone "$zone" --project "$SCRIM_PROJECT_ID"

FUNCTIONS_BUILD_SERVICE_ACCOUNT="$(functions_build_service_account "$SCRIM_PROJECT_ID")"

ensure_riot_api_secret "$SCRIM_PROJECT_ID" "$RIOT_API_KEY"
package_detail_filling_function_source "$ROOT_DIR" false clean package -DskipTests -Dspotless.skip=true
deploy_detail_filling_functions "$ROOT_DIR" "$SCRIM_REGION" "$FUNCTIONS_BUILD_SERVICE_ACCOUNT"
discover_detail_filling_domain "$SCRIM_REGION"

echo "installing VPA CRDs..."
git clone https://github.com/kubernetes/autoscaler.git || true
cp -f ./scripts/gencerts.sh ./autoscaler/vertical-pod-autoscaler/pkg/admission-controller/
cd autoscaler/vertical-pod-autoscaler
./hack/vpa-up.sh
cd ../..
rm -r autoscaler &
wait

helm dependency update "$ROOT_DIR/k8s/charts/scrimfinder"

#helm upgrade --install scrimfinder "$ROOT_DIR/k8s/charts/scrimfinder" \
#  --namespace "$SCRIM_NAMESPACE" \
#  --create-namespace \
#  --wait \
#  --timeout 25m \
#  --set global.namespace="$SCRIM_NAMESPACE" \
#  --set global.projectID="$SCRIM_PROJECT_ID" \
#  --set global.microservicesRegistry="$registry" \
#  --set global.imageTag="$SCRIM_IMAGE_TAG" \
#  --set global.useSecretManager=true \
#  --set global.useArgoApplications=true \
#  --set global.useVerticalPodAutoscaler=true \
#  --set detailFillingExternal.enabled=true \
#  --set detailFillingExternal.externalName="${DETAIL_FILLING_DOMAIN}" \
#  --set global.rabbitmqHost="$SCRIM_RABBITMQ_HOST" \
#  --set global.rabbitmqPort="$SCRIM_RABBITMQ_PORT" \
#  --set matchmaking-db.readReplicas.count=0 \
#  --set ranking-db.readReplicas.count=0 \
#  --set history-db.readReplicas.count=0 \
#  --set secrets.riotApiKey="$RIOT_API_KEY" \
#  --set secrets.dbUser="$SCRIM_DB_USER" \
#  --set secrets.dbPassword="$SCRIM_DB_PASSWORD" \
#  --set secrets.redisPassword="$SCRIM_REDIS_PASSWORD" \
#  --set secrets.rabbitmqUser="$SCRIM_RABBITMQ_USER" \
#  --set secrets.rabbitmqPassword="$SCRIM_RABBITMQ_PASSWORD" \
#  --set secrets.rabbitmqErlangCookie="$SCRIM_RABBITMQ_ERLANG_COOKIE" \
#  --set services.detail-filling-service.enabled=false \
#  --set services.ranking-service.env.DETAIL_FILLING_SERVICE_URL="http://scrimfinder-traefik/api/v1/riot" \
#  --set services.match-history-service.env.PLAYER_FILLING_SVC_URL="http://scrimfinder-traefik/api/v1/riot" \
#  --set services.training-service.env.DETAIL_FILLING_URL="http://scrimfinder-traefik/api/v1/riot"

echo "deploying Argo CD..."

export SCRIM_NAMESPACE="${SCRIM_NAMESPACE}"
export PROJECT_ID="${SCRIM_PROJECT_ID}"
export REGION="${SCRIM_REGION}"
export REPO_NAME="${SCRIM_REPO_NAME}"
export SCRIM_IMAGE_TAG="${SCRIM_IMAGE_TAG:-latest}"
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

base_url=""
for _ in $(seq 1 90); do
  base_url="$(kubectl get svc scrimfinder-traefik -n "$SCRIM_NAMESPACE" -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || true)"
  if [ -z "$base_url" ]; then
    base_url="$(kubectl get svc scrimfinder-traefik -n "$SCRIM_NAMESPACE" -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || true)"
  fi
  [ -n "$base_url" ] && break
  sleep 10
done

if [ -z "$base_url" ]; then
  echo "error: Traefik LoadBalancer address was not assigned"
  exit 1
fi

system_base_url="http://${base_url}"
echo "SCRIM_SYSTEM_BASE_URL=$system_base_url"
echo "BASE_URL=$system_base_url"

if [ -n "${GITHUB_ENV:-}" ]; then
  {
    echo "SCRIM_SYSTEM_BASE_URL=$system_base_url"
    echo "BASE_URL=$system_base_url"
  } >> "$GITHUB_ENV"
fi

if [ -n "${GITHUB_OUTPUT:-}" ]; then
  echo "base_url=$system_base_url" >> "$GITHUB_OUTPUT"
fi
