#!/bin/bash
set -e

REQUIRED_VARS="SCRIM_PROJECT_ID SCRIM_REGION SCRIM_REPO_NAME SCRIM_ENV_TAG SCRIM_CLUSTER_NAME RIOT_API_KEY SCRIM_DB_USER SCRIM_DB_PASSWORD SCRIM_PULUMI_STACK SCRIM_JWT_SECRET SCRIM_DISCORD_BOT_SECRET"

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

PULUMI_STACK="${SCRIM_PULUMI_STACK}"
PULUMI_DIR="$(cd "$(dirname "$0")/../infrastructure/pulumi" && pwd)"

echo "provisioning GKE cluster via Pulumi (stack: $PULUMI_STACK)..."

if ! command -v pulumi &>/dev/null; then
    echo "error: pulumi is not installed. See https://www.pulumi.com/docs/install/"
    exit 1
fi

(
    cd "$PULUMI_DIR"
    [ ! -d venv ] && python3 -m venv venv
    venv/bin/pip install -q -r requirements.txt
    pulumi stack select "$PULUMI_STACK" --create
    pulumi config set gcp:project "$PROJECT_ID" --stack "$PULUMI_STACK"
    pulumi config set gcp:region  "$REGION"     --stack "$PULUMI_STACK"
    pulumi up --yes --stack "$PULUMI_STACK"
)

echo "writing kubeconfig from Pulumi stack output..."
KUBECONFIG_PATH="${KUBECONFIG:-$HOME/.kube/config}"
mkdir -p "$(dirname "$KUBECONFIG_PATH")"
(cd "$PULUMI_DIR" && pulumi stack output kubeconfig --show-secrets --stack "$PULUMI_STACK")     > "$KUBECONFIG_PATH"
chmod 600 "$KUBECONFIG_PATH"

ARTIFACT_REGISTRY=$(cd "$PULUMI_DIR" && pulumi stack output artifact_registry --stack "$PULUMI_STACK")
ZONE="${REGION}-a"

echo "authenticating Docker to Artifact Registry..."
gcloud auth configure-docker "$REGION-docker.pkg.dev" --quiet

if [ -z "${SERVICES:-}" ]; then
    SERVICES="matchmaking-service ranking_service match_history_service detail_filling_service training_service analysis_service"
fi

if [ "${SKIP_BUILD:-false}" != "true" ]; then
    echo "building and pushing services in parallel..."

    for SERVICE in $SERVICES; do
        (
            IMAGE_NAME=$(echo "$SERVICE" | tr '_' '-')
            IMAGE_PATH="$ARTIFACT_REGISTRY/$IMAGE_NAME:latest"

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

echo "deploying with Helm, Traefik, and routing..."

helm repo add traefik https://traefik.github.io/charts
helm repo update

echo "preparing namespace..."
kubectl create namespace "$SCRIM_NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

echo "installing/updating Traefik CRDs explicitly..."
helm show crds traefik/traefik | kubectl apply --server-side --force-conflicts -f -

echo "updating Helm dependencies..."
helm dependency update k8s/charts/scrimfinder

COMMON_SET_ARGS=(
    --set "global.namespace=$SCRIM_NAMESPACE"
    --set "global.microservicesRegistry=${ARTIFACT_REGISTRY}"
    --set "global.region=${REGION}"
    --set "global.projectId=${PROJECT_ID}"
    --set "global.repoName=${REPO_NAME}"
    --set "secrets.riotApiKey=${RIOT_API_KEY}"
    --set "secrets.dbUser=${SCRIM_DB_USER}"
    --set "secrets.dbPassword=${SCRIM_DB_PASSWORD}"
    --set "secrets.redisPassword=${SCRIM_REDIS_PASSWORD}"
    --set "secrets.rabbitmqUser=${SCRIM_RABBITMQ_USER}"
    --set "secrets.rabbitmqPassword=${SCRIM_RABBITMQ_PASSWORD}"
    --set "secrets.rabbitmqErlangCookie=${SCRIM_RABBITMQ_ERLANG_COOKIE}"
    --set "global.rabbitmqHost=${SCRIM_RABBITMQ_HOST}"
    --set "global.rabbitmqPort=${SCRIM_RABBITMQ_PORT}"
    --set "secrets.jwtSecret=${SCRIM_JWT_SECRET}"
    --set "secrets.discordBotSecret=${SCRIM_DISCORD_BOT_SECRET}"
)

HELM_DIFF_ARGS=(
    --namespace "$SCRIM_NAMESPACE"
    "${COMMON_SET_ARGS[@]}"
)

HELM_UPGRADE_ARGS=(
    --namespace "$SCRIM_NAMESPACE"
    --create-namespace
    --skip-crds
    "${COMMON_SET_ARGS[@]}"
)

if helm plugin list | grep -q "diff"; then
    echo "--- PREVIEWING CHANGES (helm diff) ---"

    helm diff upgrade scrimfinder k8s/charts/scrimfinder \
        "${HELM_DIFF_ARGS[@]}" \
        --allow-unreleased

    if [ "${CONFIRM_DEPLOY:-false}" = "true" ]; then
        echo "proceeding with deployment..."
    else
        echo "check the diff above."
        echo "to bypass this check in the future, set CONFIRM_DEPLOY=true."
    fi
fi

echo "checking Helm release state..."

HELM_STATUS=$(helm status scrimfinder -n "$SCRIM_NAMESPACE" 2>/dev/null | grep "STATUS:" | awk '{print $2}' || echo "NOT_FOUND")

if [[ "$HELM_STATUS" == "pending-install" || "$HELM_STATUS" == "pending-upgrade" || "$HELM_STATUS" == "pending-rollback" ]]; then
    echo "detected stuck Helm release ($HELM_STATUS). clearing lock..."

    LATEST_REVISION=$(helm history scrimfinder -n "$SCRIM_NAMESPACE" --max 1 2>/dev/null | tail -n 1 | awk '{print $1}')

    if [ -n "$LATEST_REVISION" ] && [ "$LATEST_REVISION" != "REVISION" ]; then
        kubectl delete secret "sh.helm.release.v1.scrimfinder.v${LATEST_REVISION}" -n "$SCRIM_NAMESPACE"
    fi
fi

echo "deploying with Helm..."

set +e
HELM_OUTPUT=$(helm upgrade --install scrimfinder k8s/charts/scrimfinder \
    "${HELM_UPGRADE_ARGS[@]}" \
    --wait \
    --timeout 10m 2>&1)
HELM_RC=$?
set -e

if [ $HELM_RC -ne 0 ]; then
    echo "$HELM_OUTPUT"
    echo "helm deployment failed."
    exit $HELM_RC
else
    echo "$HELM_OUTPUT"
fi

echo "waiting for Traefik LoadBalancer External IP/Hostname..."

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

echo "deployment complete! Traefik External IP/Hostname: $EXTERNAL_IP"

echo "verifying service health with internal smoke tests..."
helm test scrimfinder --namespace "$SCRIM_NAMESPACE"