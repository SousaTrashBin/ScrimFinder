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

helm dependency update "$ROOT_DIR/k8s/charts/scrimfinder"

helm upgrade --install scrimfinder "$ROOT_DIR/k8s/charts/scrimfinder" \
  --namespace "$SCRIM_NAMESPACE" \
  --create-namespace \
  --wait \
  --timeout 25m \
  --set global.namespace="$SCRIM_NAMESPACE" \
  --set global.projectID="$SCRIM_PROJECT_ID" \
  --set global.microservicesRegistry="$registry" \
  --set global.imageTag="$SCRIM_IMAGE_TAG" \
  --set global.useSecretManager=false \
  --set global.useArgoApplications=false \
  --set detailFillingExternal.enabled=true \
  --set detailFillingExternal.externalName="${DETAIL_FILLING_DOMAIN}" \
  --set global.rabbitmqHost="$SCRIM_RABBITMQ_HOST" \
  --set global.rabbitmqPort="$SCRIM_RABBITMQ_PORT" \
  --set secrets.riotApiKey="$RIOT_API_KEY" \
  --set secrets.dbUser="$SCRIM_DB_USER" \
  --set secrets.dbPassword="$SCRIM_DB_PASSWORD" \
  --set secrets.redisPassword="$SCRIM_REDIS_PASSWORD" \
  --set secrets.rabbitmqUser="$SCRIM_RABBITMQ_USER" \
  --set secrets.rabbitmqPassword="$SCRIM_RABBITMQ_PASSWORD" \
  --set secrets.rabbitmqErlangCookie="$SCRIM_RABBITMQ_ERLANG_COOKIE" \
  --set services.detail-filling-service.enabled=false \
  --set services.ranking-service.env.DETAIL_FILLING_SERVICE_URL="http://scrimfinder-traefik/api/v1/riot" \
  --set services.match-history-service.env.PLAYER_FILLING_SVC_URL="http://scrimfinder-traefik/api/v1/riot" \
  --set services.training-service.env.DETAIL_FILLING_URL="http://scrimfinder-traefik/api/v1/riot"

kubectl rollout status deployment/matchmaking-service -n "$SCRIM_NAMESPACE" --timeout=10m
kubectl rollout status deployment/ranking-service -n "$SCRIM_NAMESPACE" --timeout=10m
kubectl rollout status deployment/match-history-service -n "$SCRIM_NAMESPACE" --timeout=10m
kubectl rollout status deployment/training-service -n "$SCRIM_NAMESPACE" --timeout=10m
kubectl rollout status deployment/analysis-service -n "$SCRIM_NAMESPACE" --timeout=10m

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
