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

echo "creating secrets for Google Cloud..."

SECRETS_SERVICE_ACCOUNT=secrets-service-account
SECRETS_SERVICE_ACCOUNT_EMAIL="${SECRETS_SERVICE_ACCOUNT}@${PROJECT_ID}.iam.gserviceaccount.com"

gcloud iam service-accounts create $SECRETS_SERVICE_ACCOUNT \
    --description="A Service Account with access to all ScrimFinder secrets" \
    --display-name="secrets-service-account" || true

SECRETS="RIOT_API_KEY|${RIOT_API_KEY}"
SECRETS+=" DB_USER|${SCRIM_DB_USER}"
SECRETS+=" DB_PASSWORD|${SCRIM_DB_PASSWORD}"
SECRETS+=" REDIS_PASSWORD|${SCRIM_REDIS_PASSWORD}"
SECRETS+=" DB_USER|${SCRIM_DB_USER}"
SECRETS+=" RABBITMQ_USER|${SCRIM_RABBITMQ_USER}"
SECRETS+=" RABBITMQ_PASSWORD|${SCRIM_RABBITMQ_PASSWORD}"
SECRETS+=" RABBITMQ_ERLANG_COOKIE|${SCRIM_RABBITMQ_ERLANG_COOKIE}"

for NAME_SECRET in ${SECRETS}; do
    (
        NAME=${NAME_SECRET%%|*}
        SECRET=${NAME_SECRET##*|}

        echo -n "${SECRET}" | gcloud secrets create "${NAME}" \
            --data-file=- \
            --replication-policy="automatic" || true

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

SERVERLESS_FUNCTIONS="detail_filling_service|getFilledMatch|RIOT_API_KEY"
SERVERLESS_FUNCTIONS+=" detail_filling_service|getRawMatchData|RIOT_API_KEY"
SERVERLESS_FUNCTIONS+=" detail_filling_service|getFilledPlayer|RIOT_API_KEY"

for SERVICE_FUNCTION in ${SERVERLESS_FUNCTIONS}; do
    (
        SERVICE=${SERVICE_FUNCTION%%|*}
        temp=${SERVICE_FUNCTION#*|}
        FUNCTION=${temp%%|*}
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
            --service-account="${SECRETS_SERVICE_ACCOUNT_EMAIL}" \
            --set-secrets 'RIOT_API_KEY=RIOT_API_KEY:latest' # from Google Cloud secret manager
    ) &
done

wait
echo "all serverless functions done deploying."

echo "functions info:"

for SERVICE_FUNCTION in ${SERVERLESS_FUNCTIONS}; do
    temp=${SERVICE_FUNCTION#*|}
    FUNCTION=${temp%%|*}
    gcloud functions describe "${FUNCTION}" --region="${REGION}" --format="value(url)"
done

echo "updating Helm dependencies..."
helm dependency update k8s/charts/scrimfinder

echo "deploying Argo CD..."
kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -n argocd --server-side --force-conflicts -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl patch svc argocd-server -n argocd -p '{"spec": {"type": "LoadBalancer"}}'
kubectl apply -n argocd --server-side --force-conflicts -f k8s/application.yaml

COMMON_SET_ARGS=(
    --set "global.namespace=$SCRIM_NAMESPACE"
    --set "global.projectID=$PROJECT_ID"
    --set "global.microservicesRegistry=${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO_NAME}"
    --set "global.region=${REGION}"
    --set "global.projectId=${PROJECT_ID}"
    --set "global.repoName=${REPO_NAME}"
    --set "global.rabbitmqHost=${SCRIM_RABBITMQ_HOST}"
    --set "global.rabbitmqPort=${SCRIM_RABBITMQ_PORT}"
)

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

echo "verifying service health with internal smoke tests..."
helm test scrimfinder --namespace "$SCRIM_NAMESPACE"

echo "deployment complete!"
echo "Traefik External IP/Hostname: $EXTERNAL_IP"
echo "Argo CD External IP/Hostname: ${EXTERNAL_ARGOCD_IP}; username: admin; initial password: $INITIAL_ARGOCD_PASSWORD"
