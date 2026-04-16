#!/bin/bash
set -e

REQUIRED_VARS=("SCRIM_PROJECT_ID" "SCRIM_REGION" "SCRIM_REPO_NAME" "SCRIM_ENV_TAG" "SCRIM_CLUSTER_NAME" "DASHBOARD_USER" "DASHBOARD_PASSWORD")
for var in "${REQUIRED_VARS[@]}"; do
    if [ -z "${!var}" ]; then
        echo "rrror: $var is not set. please set it in your system environment."
        exit 1
    fi
done

PROJECT_ID="$SCRIM_PROJECT_ID"
REGION="$SCRIM_REGION"
REPO_NAME="$SCRIM_REPO_NAME"
ENV_TAG="$SCRIM_ENV_TAG"
CLUSTER_NAME="$SCRIM_CLUSTER_NAME"

echo "setting active GCP project to $PROJECT_ID..."
gcloud config set project "$PROJECT_ID" --quiet

echo "enabling required Google Cloud APIs..."
gcloud services enable \
    artifactregistry.googleapis.com \
    container.googleapis.com \
    compute.googleapis.com \
    iam.googleapis.com

if ! gcloud container clusters describe "$CLUSTER_NAME" --region "$REGION" > /dev/null 2>&1; then
    echo "creating GKE cluster: $CLUSTER_NAME ..."
    gcloud container clusters create "$CLUSTER_NAME" \
        --region "$REGION" \
        --num-nodes 1 \
        --machine-type e2-small \
        --spot \
        --enable-autoscaling --min-nodes 1 --max-nodes 2 \
        --quiet
else
    echo "cluster $CLUSTER_NAME already exists."
fi

echo "authenticating Docker to Artifact Registry..."
gcloud auth configure-docker "$REGION-docker.pkg.dev" --quiet

if ! gcloud artifacts repositories describe "$REPO_NAME" --location="$REGION" > /dev/null 2>&1; then
    echo "creating Artifact Registry repository: $REPO_NAME..."
    gcloud artifacts repositories create "$REPO_NAME" --repository-format=docker --location="$REGION" --description="Docker repository for ScrimFinder"
else
    echo "repository $REPO_NAME already exists."
fi

SERVICES=("matchmaking-service" "ranking_service" "match_history_service" "detail_filling_service" "training_service" "analysis_service")

for SERVICE in "${SERVICES[@]}"; do
    IMAGE_NAME=$(echo "$SERVICE" | tr '_' '-')
    IMAGE_PATH="$REGION-docker.pkg.dev/$PROJECT_ID/$REPO_NAME/$IMAGE_NAME:latest"
    echo "building and pushing $SERVICE (Tag: $IMAGE_NAME) for linux/amd64..."
    docker buildx build --platform linux/amd64 -t "$IMAGE_PATH" "./$SERVICE" --push
done

echo "deployment images pushed successfully to $REGION!"

echo "fetching GKE credentials..."
gcloud container clusters get-credentials "$CLUSTER_NAME" --region "$REGION" --project "$PROJECT_ID"

echo "applying Kubernetes manifests (with envsubst)..."

kubectl apply -f https://raw.githubusercontent.com/traefik/traefik/v3.0/docs/content/reference/dynamic-configuration/kubernetes-crd-definition-v1.yml
echo "waiting for Traefik CRDs to be established..."
kubectl wait --for=condition=established --timeout=60s crd/ingressroutes.traefik.io
kubectl wait --for=condition=established --timeout=60s crd/middlewares.traefik.io

kubectl apply -f k8s/traefik/rbac.yaml
kubectl apply -f k8s/apps/infrastructure.yaml
kubectl apply -f k8s/traefik/deployment.yaml

export PROJECT_ID REGION REPO_NAME ENV_TAG CLUSTER_NAME DASHBOARD_USER DASHBOARD_PASSWORD
envsubst < k8s/traefik/middlewares.yaml | kubectl apply -f -
envsubst < k8s/apps/scrimfinder-apps.yaml | kubectl apply -f -
kubectl apply -f k8s/traefik/ingressroutes.yaml

kubectl rollout restart deployment traefik -n kube-system

echo "waiting for Traefik LoadBalancer External IP..."
EXTERNAL_IP=""
while [ -z "$EXTERNAL_IP" ]; do
    echo "waiting for IP..."
    EXTERNAL_IP=$(kubectl get svc traefik -n kube-system -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
    [ -z "$EXTERNAL_IP" ] && sleep 10
done

echo "deployment complete! Traefik External IP: $EXTERNAL_IP"
