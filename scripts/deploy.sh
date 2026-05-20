#!/bin/bash
set -e

REQUIRED_VARS="SCRIM_PROJECT_ID SCRIM_REGION SCRIM_REPO_NAME SCRIM_ENV_TAG SCRIM_CLUSTER_NAME RIOT_API_KEY SCRIM_DB_USER SCRIM_DB_PASSWORD SCRIM_GRAFANA_TOKEN"

for var in $REQUIRED_VARS; do
    if [ -z "$(eval echo \${$var:-})" ]; then
        echo "error: $var is not set. please set it in your system environment."
        exit 1
    fi
done

PROJECT_ID="$SCRIM_PROJECT_ID"
REGION="$SCRIM_REGION"
REPO_NAME="$SCRIM_REPO_NAME"
ENV_TAG="$SCRIM_ENV_TAG"
CLUSTER_NAME="$SCRIM_CLUSTER_NAME"

SCRIM_NAMESPACE="${SCRIM_NAMESPACE:-scrimfinder}"

SCRIM_REDIS_PASSWORD="${SCRIM_REDIS_PASSWORD:-redispassword}"
SCRIM_RABBITMQ_USER="${SCRIM_RABBITMQ_USER:-user}"
SCRIM_RABBITMQ_PASSWORD="${SCRIM_RABBITMQ_PASSWORD:-rabbitmqpassword}"
SCRIM_RABBITMQ_ERLANG_COOKIE="${SCRIM_RABBITMQ_ERLANG_COOKIE:-erlangcookie}"
SCRIM_RABBITMQ_HOST="${SCRIM_RABBITMQ_HOST:-scrimfinder-rabbitmq-broker}"
SCRIM_RABBITMQ_PORT="${SCRIM_RABBITMQ_PORT:-5672}"
SCRIM_ML_DB_PASSWORD="${SCRIM_ML_DB_PASSWORD:-$SCRIM_DB_PASSWORD}"
SCRIM_JWT_DB_PASSWORD="${SCRIM_JWT_DB_PASSWORD:-$SCRIM_DB_PASSWORD}"
SCRIM_JWT_SECRET="${SCRIM_JWT_SECRET:-changeme_in_production}"
SCRIM_DISCORD_BOT_SECRET="${SCRIM_DISCORD_BOT_SECRET:-changeme_in_production}"
SCRIM_JWT_PRIVATE_KEY="${SCRIM_JWT_PRIVATE_KEY:-}"
SCRIM_JWT_PUBLIC_KEY="${SCRIM_JWT_PUBLIC_KEY:-}"

echo "checking GCP configuration..."
gcloud config set project "$PROJECT_ID" --quiet

echo "enabling required Google Cloud APIs..."
gcloud services enable \
    artifactregistry.googleapis.com \
    container.googleapis.com \
    compute.googleapis.com \
    iam.googleapis.com \
    cloudfunctions.googleapis.com \
    cloudbuild.googleapis.com \
    run.googleapis.com \
    eventarc.googleapis.com \
    secretmanager.googleapis.com

PROJECT_NUMBER=$(gcloud projects describe "$PROJECT_ID" --format="value(projectNumber)")
FUNCTIONS_BUILD_SERVICE_ACCOUNT_EMAIL="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"
FUNCTIONS_BUILD_SERVICE_ACCOUNT="projects/${PROJECT_ID}/serviceAccounts/${FUNCTIONS_BUILD_SERVICE_ACCOUNT_EMAIL}"

echo "ensuring Cloud Functions build service account permissions..."
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:${FUNCTIONS_BUILD_SERVICE_ACCOUNT_EMAIL}" \
    --role="roles/cloudbuild.builds.builder" \
    --quiet

ZONE="${REGION}-a"
EXPECTED_CONTEXT="gke_${PROJECT_ID}_${ZONE}_${CLUSTER_NAME}"

if [ "${SCRIM_SKIP_TERRAFORM:-false}" != "true" ]; then
    if ! command -v terraform >/dev/null 2>&1; then
        echo "error: terraform is required unless SCRIM_SKIP_TERRAFORM=true."
        exit 1
    fi

    echo "applying Terraform infrastructure..."
    terraform -chdir=infrastructure/terraform init -input=false
    terraform -chdir=infrastructure/terraform apply -auto-approve \
        -var="project_id=${PROJECT_ID}" \
        -var="region=${REGION}" \
        -var="cluster_name=${CLUSTER_NAME}" \
        -var="repo_name=${REPO_NAME}" \
        -var="environment=${ENV_TAG}" \
        -var="namespace=${SCRIM_NAMESPACE}"
fi

CURRENT_CONTEXT=$(kubectl config current-context 2>/dev/null || echo "")
if [[ "$CURRENT_CONTEXT" != "$EXPECTED_CONTEXT" ]]; then
    echo "fetching GKE credentials for Terraform-managed cluster..."
    gcloud container clusters get-credentials "$CLUSTER_NAME" \
        --zone "$ZONE" \
        --project "$PROJECT_ID"
else
    echo "already connected to the correct GKE cluster ($EXPECTED_CONTEXT)."
fi

echo "creating secrets for Google Cloud..."

SECRETS_SERVICE_ACCOUNT=secrets-service-account
SECRETS_SERVICE_ACCOUNT_EMAIL="${SECRETS_SERVICE_ACCOUNT}@${PROJECT_ID}.iam.gserviceaccount.com"

gcloud iam service-accounts create $SECRETS_SERVICE_ACCOUNT \
    --description="A Service Account with access to all ScrimFinder secrets" \
    --display-name="secrets-service-account" || true

gcloud iam service-accounts add-iam-policy-binding "${SECRETS_SERVICE_ACCOUNT_EMAIL}" \
    --member="serviceAccount:${PROJECT_ID}.svc.id.goog[${SCRIM_NAMESPACE}/scrimfinder-secrets-reader]" \
    --role="roles/iam.workloadIdentityUser" \
    --quiet || true

SECRETS="RIOT_API_KEY|${RIOT_API_KEY}"
SECRETS+=" riot-api-key|${RIOT_API_KEY}"
SECRETS+=" db-user|${SCRIM_DB_USER}"
SECRETS+=" db-password|${SCRIM_DB_PASSWORD}"
SECRETS+=" ml-db-password|${SCRIM_ML_DB_PASSWORD}"
SECRETS+=" jwt-db-password|${SCRIM_JWT_DB_PASSWORD}"
SECRETS+=" redis-password|${SCRIM_REDIS_PASSWORD}"
SECRETS+=" rabbitmq-user|${SCRIM_RABBITMQ_USER}"
SECRETS+=" rabbitmq-password|${SCRIM_RABBITMQ_PASSWORD}"
SECRETS+=" rabbitmq-erlang-cookie|${SCRIM_RABBITMQ_ERLANG_COOKIE}"
SECRETS+=" jwt-secret|${SCRIM_JWT_SECRET}"
SECRETS+=" discord-bot-secret|${SCRIM_DISCORD_BOT_SECRET}"
SECRETS+=" jwt-private-key|${SCRIM_JWT_PRIVATE_KEY}"
SECRETS+=" jwt-public-key|${SCRIM_JWT_PUBLIC_KEY}"

for NAME_SECRET in ${SECRETS}; do
    (
        NAME=${NAME_SECRET%%|*}
        SECRET=${NAME_SECRET##*|}

        if gcloud secrets describe "${NAME}" > /dev/null 2>&1; then
            echo -n "${SECRET}" | gcloud secrets versions add "${NAME}" \
                --data-file=-
        else
            echo -n "${SECRET}" | gcloud secrets create "${NAME}" \
                --data-file=- \
                --replication-policy="automatic"
        fi

        gcloud secrets add-iam-policy-binding "${NAME}" \
            --member="serviceAccount:${SECRETS_SERVICE_ACCOUNT_EMAIL}" \
            --role="roles/secretmanager.secretAccessor"
    ) &
done

wait
echo "done creating secrets."

echo "authenticating Docker to Artifact Registry..."
gcloud auth configure-docker "$REGION-docker.pkg.dev" --quiet

if ! gcloud artifacts repositories describe "$REPO_NAME" --location="$REGION" > /dev/null 2>&1; then
    echo "creating Artifact Registry repository: $REPO_NAME..."

    gcloud artifacts repositories create "$REPO_NAME" \
        --repository-format=docker \
        --location="$REGION" \
        --description="Docker repository for ScrimFinder"
fi

if [ -z "${SERVICES:-}" ]; then
    SERVICES="matchmaking-service ranking_service match_history_service training_service analysis_service"
fi

if [ "${SKIP_BUILD:-false}" != "true" ]; then
    echo "building and pushing services in parallel..."

    for SERVICE in $SERVICES; do
        (
            IMAGE_NAME=$(echo "$SERVICE" | tr '_' '-')
            IMAGE_PATH="$REGION-docker.pkg.dev/$PROJECT_ID/$REPO_NAME/$IMAGE_NAME:latest"

            echo "building $SERVICE..."
            docker buildx build \
                --platform linux/amd64 \
                -t "$IMAGE_PATH" \
                "./$SERVICE" \
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

echo "packaging services with serverless functions..."

SERVERLESS_SERVICES="detail_filling_service"

for SERVICE in ${SERVERLESS_SERVICES}; do
    cd "${SERVICE}"
    mvn clean package
    cd ..
done

echo "deploying serverless functions in parallel..."

SERVERLESS_FUNCTIONS="detail_filling_service|getFilledMatch|riot_api_key"
SERVERLESS_FUNCTIONS+=" detail_filling_service|getRawMatchData|riot_api_key"
SERVERLESS_FUNCTIONS+=" detail_filling_service|getFilledPlayer|riot_api_key"

SERVERLESS_DEPLOY_PIDS=()
SERVERLESS_DEPLOY_NAMES=()

for SERVICE_FUNCTION in ${SERVERLESS_FUNCTIONS}; do
    temp=${SERVICE_FUNCTION#*|}
    FUNCTION=${temp%%|*}

    (
        SERVICE=${SERVICE_FUNCTION%%|*}
        SECRETS=${temp##*|}

        gcloud functions deploy "${FUNCTION}" \
            --region="${REGION}" \
            --entry-point=io.quarkus.gcp.functions.http.QuarkusHttpFunction \
            --runtime=java21 \
            --trigger-http \
            --allow-unauthenticated \
            --source="${SERVICE}"/target/deployment \
            --min-instances=0 \
            --max-instances=30 \
            --memory=512Mi \
            --cpu=800m \
            --build-service-account="${FUNCTIONS_BUILD_SERVICE_ACCOUNT}" \
            --service-account="${SECRETS_SERVICE_ACCOUNT_EMAIL}" \
            --set-secrets 'RIOT_API_KEY=RIOT_API_KEY:latest' # from Google Cloud secret manager
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

echo "all serverless functions done deploying."

echo "function endpoints:"

DETAIL_FILLING_FUNCTION_URL=""
DETAIL_FILLING_API_URL=""

for SERVICE_FUNCTION in ${SERVERLESS_FUNCTIONS}; do
    temp=${SERVICE_FUNCTION#*|}
    FUNCTION=${temp%%|*}
    FUNCTION_URL=$(gcloud functions describe "${FUNCTION}" --region="${REGION}" --format="value(url)")

    if [ -z "${DETAIL_FILLING_FUNCTION_URL}" ]; then
        DETAIL_FILLING_FUNCTION_URL="${FUNCTION_URL}"
        DETAIL_FILLING_DOMAIN=$(echo "$FUNCTION_URL" | awk -F/ '{print $3}')
    fi

    case "${FUNCTION}" in
        getFilledMatch)
            echo "${FUNCTION}: ${FUNCTION_URL}/api/v1/riot/matches/{matchId}"
            ;;
        getRawMatchData)
            echo "${FUNCTION}: ${FUNCTION_URL}/api/v1/riot/matches/{matchId}/raw"
            ;;
        getFilledPlayer)
            echo "${FUNCTION}: ${FUNCTION_URL}/api/v1/riot/players/{server}/{name}/{tag}"
            ;;
        *)
            echo "${FUNCTION}: ${FUNCTION_URL}"
            ;;
    esac
done

if [ -z "${DETAIL_FILLING_FUNCTION_URL}" ]; then
    echo "error: no detail filling serverless function URL was found."
    exit 1
fi

echo "using detail filling domain for Traefik ExternalName: ${DETAIL_FILLING_DOMAIN}"

echo "updating Helm dependencies..."
helm dependency update k8s/charts/scrimfinder

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

echo "instrumenting app with Grafana Alloy to send metrics to Grafana Cloud..."

kubectl get ns beyla >/dev/null 2>&1 || kubectl create ns beyla && helm repo add grafana https://grafana.github.io/helm-charts && \
  helm repo update && \
  { kubectl apply -f - <<'EOF'
apiVersion: v1
kind: Secret
metadata:
  name: grafana-secret
  namespace: beyla
type: Opaque
stringData:
  otlp-headers: "Authorization=Basic ${SCRIM_GRAFANA_TOKEN}"
EOF
} && echo "secret created" && \
helm upgrade --install --atomic --timeout 300s beyla grafana/beyla \
  --namespace beyla --create-namespace \
  --values - <<'EOF'
config:
  data:
    discovery:
      instrument:
        - k8s_namespace: production
        - k8s_namespace: staging
    routes:
      unmatched: heuristic
    env:
      OTEL_EXPORTER_OTLP_ENDPOINT: "http://grafana-k8s-monitoring-alloy-receiver.scrimfinder.svc.cluster.local:4317"
    envValueFrom:
      OTEL_EXPORTER_OTLP_HEADERS:
        secretKeyRef:
          name: grafana-secret
          key: otlp-headers
EOF

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

echo "deployment complete!"
echo "Traefik External IP/Hostname: $EXTERNAL_IP"
echo "Argo CD External IP/Hostname: ${EXTERNAL_ARGOCD_IP}; username: admin; initial password: $INITIAL_ARGOCD_PASSWORD"
