#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

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

PROJECT_NUMBER=$(gcloud projects describe "$SCRIM_PROJECT_ID" --format="value(projectNumber)")
FUNCTIONS_BUILD_SERVICE_ACCOUNT_EMAIL="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"
FUNCTIONS_BUILD_SERVICE_ACCOUNT="projects/${SCRIM_PROJECT_ID}/serviceAccounts/${FUNCTIONS_BUILD_SERVICE_ACCOUNT_EMAIL}"
SECRETS_SERVICE_ACCOUNT_EMAIL="secrets-service-account@${SCRIM_PROJECT_ID}.iam.gserviceaccount.com"

echo "packaging services with serverless functions..."

SERVERLESS_SERVICES="detail_filling_service"

for SERVICE in ${SERVERLESS_SERVICES}; do
    cd "${ROOT_DIR}/${SERVICE}"
    mvn clean package -DskipTests -Dspotless.skip=true
    cd "${ROOT_DIR}"
done

echo "deploying serverless functions in parallel..."

SERVERLESS_FUNCTIONS="detail_filling_service|getFilledMatch|RIOT_API_KEY"
SERVERLESS_FUNCTIONS+=" detail_filling_service|getRawMatchData|RIOT_API_KEY"
SERVERLESS_FUNCTIONS+=" detail_filling_service|getFilledPlayer|RIOT_API_KEY"

SERVERLESS_DEPLOY_PIDS=()
SERVERLESS_DEPLOY_NAMES=()

for SERVICE_FUNCTION in ${SERVERLESS_FUNCTIONS}; do
    temp=${SERVICE_FUNCTION#*|}
    FUNCTION=${temp%%|*}

    (
        SERVICE=${SERVICE_FUNCTION%%|*}

        gcloud functions deploy "${FUNCTION}" \
            --region="${SCRIM_REGION}" \
            --entry-point=io.quarkus.gcp.functions.http.QuarkusHttpFunction \
            --runtime=java21 \
            --trigger-http \
            --allow-unauthenticated \
            --source="${ROOT_DIR}/${SERVICE}"/target/deployment \
            --min-instances=0 \
            --max-instances=30 \
            --memory=512Mi \
            --cpu=800m \
            --build-service-account="${FUNCTIONS_BUILD_SERVICE_ACCOUNT}" \
            --set-secrets 'RIOT_API_KEY=RIOT_API_KEY:latest' # assuming standard secret manager setup, no secrets service account used here for brevity as it might not be created in ephemeral script
    ) &

    SERVERLESS_DEPLOY_PIDS+=("$!")
    SERVERLESS_DEPLOY_NAMES+=("${FUNCTION}")
done

SERVERLESS_DEPLOY_FAILED=0
for i in "${!SERVERLESS_DEPLOY_PIDS[@]}"; do
    if ! wait "${SERVERLESS_DEPLOY_PIDS[$i]}"; then
        echo "serverless function deploy failed: ${SERVERLESS_DEPLOY_NAMES[$i]}"
        SERVERLESS_DEPLOY_FAILED=1
    fi
done

if [ "$SERVERLESS_DEPLOY_FAILED" -ne 0 ]; then
    exit 1
fi

echo "function endpoints:"

DETAIL_FILLING_FUNCTION_URL=""
DETAIL_FILLING_DOMAIN=""

for SERVICE_FUNCTION in ${SERVERLESS_FUNCTIONS}; do
    temp=${SERVICE_FUNCTION#*|}
    FUNCTION=${temp%%|*}
    FUNCTION_URL=$(gcloud functions describe "${FUNCTION}" --region="${SCRIM_REGION}" --format="value(url)")

    if [ -z "${DETAIL_FILLING_FUNCTION_URL}" ]; then
        DETAIL_FILLING_FUNCTION_URL="${FUNCTION_URL}"
        DETAIL_FILLING_DOMAIN=$(echo "$FUNCTION_URL" | awk -F/ '{print $3}')
    fi
done

if [ -z "${DETAIL_FILLING_FUNCTION_URL}" ]; then
    echo "error: no detail filling serverless function URL was found."
    exit 1
fi

echo "using detail filling domain for Traefik ExternalName: ${DETAIL_FILLING_DOMAIN}"

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
