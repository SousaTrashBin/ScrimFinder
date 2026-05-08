#!/bin/bash
set -e

REQUIRED_VARS="SCRIM_PROJECT_ID SCRIM_REGION SCRIM_REPO_NAME SCRIM_ENV_TAG SCRIM_CLUSTER_NAME RIOT_API_KEY SCRIM_DB_USER SCRIM_DB_PASSWORD"

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

echo "checking GCP configuration..."
gcloud config set project "$PROJECT_ID" --quiet

if ! gcloud services list --enabled --filter="name:container.googleapis.com" | grep -q "container.googleapis.com"; then
    echo "enabling required Google Cloud APIs..."
    gcloud services enable \
        artifactregistry.googleapis.com \
        container.googleapis.com \
        compute.googleapis.com \
        iam.googleapis.com
fi

ZONE="${REGION}-a"
EXPECTED_CONTEXT="gke_${PROJECT_ID}_${ZONE}_${CLUSTER_NAME}"

CURRENT_CONTEXT=$(kubectl config current-context 2>/dev/null || echo "")

if [[ "$CURRENT_CONTEXT" != "$EXPECTED_CONTEXT" ]]; then
    echo "target GKE context not active. checking cluster status..."

    CLUSTER_STATUS=$(gcloud container clusters describe "$CLUSTER_NAME" \
        --zone "$ZONE" \
        --project "$PROJECT_ID" \
        --format="value(status)" 2>/dev/null || echo "NOT_FOUND")

    if [ "$CLUSTER_STATUS" = "NOT_FOUND" ]; then
        echo "creating GKE zonal cluster: $CLUSTER_NAME in $ZONE ..."

        gcloud container clusters create "$CLUSTER_NAME" \
            --zone "$ZONE" \
            --project "$PROJECT_ID" \
            --num-nodes 1 \
            --machine-type e2-standard-4 \
            --disk-size 40 \
            --disk-type pd-standard \
            --spot \
            --enable-autoscaling \
            --min-nodes 1 \
            --max-nodes 1 \
            --quiet

        CLUSTER_STATUS="PROVISIONING"
    fi

    echo "ensuring cluster $CLUSTER_NAME is RUNNING..."

    until [ "$CLUSTER_STATUS" = "RUNNING" ]; do
        CLUSTER_STATUS=$(gcloud container clusters describe "$CLUSTER_NAME" \
            --zone "$ZONE" \
            --project "$PROJECT_ID" \
            --format="value(status)" 2>/dev/null || echo "ERROR")

        if [ "$CLUSTER_STATUS" = "RUNNING" ]; then
            break
        fi

        echo "current status: $CLUSTER_STATUS. waiting 20s..."
        sleep 20
    done

    echo "fetching GKE credentials..."
    gcloud container clusters get-credentials "$CLUSTER_NAME" \
        --zone "$ZONE" \
        --project "$PROJECT_ID"
else
    echo "already connected to the correct GKE cluster ($EXPECTED_CONTEXT)."
fi

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
    SERVICES="matchmaking-service ranking_service match_history_service detail_filling_service training_service analysis_service"
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
    --set "global.microservicesRegistry=${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO_NAME}"
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