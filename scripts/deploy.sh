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
REPO_NAME="$SCRIM_REPO_NAME"
CLUSTER_NAME="$SCRIM_CLUSTER_NAME"

SCRIM_NAMESPACE="${SCRIM_NAMESPACE:-scrimfinder}"

SCRIM_RABBITMQ_HOST="${SCRIM_RABBITMQ_HOST:-scrimfinder-rabbitmq-broker}"
SCRIM_RABBITMQ_PORT="${SCRIM_RABBITMQ_PORT:-5672}"
SCRIM_IMAGE_TAG="${SCRIM_IMAGE_TAG:-latest}"

echo "checking GCP configuration..."
gcloud config set project "$PROJECT_ID" --quiet

echo "provisioning infrastructure with Terraform..."
"$(dirname "$0")/deploy-infra.sh"

PROJECT_NUMBER=$(gcloud projects describe "$PROJECT_ID" --format="value(projectNumber)")
FUNCTIONS_BUILD_SERVICE_ACCOUNT_EMAIL="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"
FUNCTIONS_BUILD_SERVICE_ACCOUNT="projects/${PROJECT_ID}/serviceAccounts/${FUNCTIONS_BUILD_SERVICE_ACCOUNT_EMAIL}"
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
    ./redisInit.sh || true
    mvn clean package
    ./redisShutdown.sh || true
    cd ..
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

echo "installing VPA CRDs..."
git clone https://github.com/kubernetes/autoscaler.git || true
cp -f ./scripts/gencerts.sh ./autoscaler/vertical-pod-autoscaler/pkg/admission-controller/
cd autoscaler/vertical-pod-autoscaler
./hack/vpa-up.sh
cd ../..
rm -r autoscaler &

wait

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