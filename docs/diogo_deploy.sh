#!/bin/bash
set -e

REQUIRED_VARS="SCRIM_PROJECT_ID SCRIM_REGION SCRIM_REPO_NAME SCRIM_ENV_TAG SCRIM_CLUSTER_NAME DASHBOARD_USER DASHBOARD_PASSWORD RIOT_API_KEY"

for var in $REQUIRED_VARS; do
    if [ -z "$(eval echo \$$var)" ]; then
        echo "error: $var is not set. please set it in your system environment."
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

echo "checking GKE cluster status..."
CLUSTER_STATUS=$(gcloud container clusters describe "$CLUSTER_NAME" --region "$REGION" --format="value(status)" 2>/dev/null || echo "NOT_FOUND")

if [ "$CLUSTER_STATUS" = "STOPPING" ] || [ "$CLUSTER_STATUS" = "DELETING" ]; then
    echo "cluster is currently being deleted. waiting for deletion to complete..."
    while [ "$CLUSTER_STATUS" != "NOT_FOUND" ]; do
        echo "current status: $CLUSTER_STATUS. waiting 20s..."
        sleep 20
        CLUSTER_STATUS=$(gcloud container clusters describe "$CLUSTER_NAME" --region "$REGION" --format="value(status)" 2>/dev/null || echo "NOT_FOUND")
    done
fi

if [ "$CLUSTER_STATUS" = "NOT_FOUND" ]; then
    echo "creating GKE cluster: $CLUSTER_NAME ..."
    gcloud container clusters create "$CLUSTER_NAME" \
        --region "$REGION" \
        --num-nodes 1 \
        --machine-type e2-medium \
        --spot \
        --enable-autoscaling --min-nodes 1 --max-nodes 2 \
        --quiet
    CLUSTER_STATUS="PROVISIONING"
fi

echo "ensuring cluster $CLUSTER_NAME is RUNNING..."
until [ "$CLUSTER_STATUS" = "RUNNING" ]; do
    CLUSTER_STATUS=$(gcloud container clusters describe "$CLUSTER_NAME" --region "$REGION" --project "$PROJECT_ID" --format="value(status)" 2>/dev/null || echo "ERROR")
    if [ "$CLUSTER_STATUS" = "RUNNING" ]; then
        break
    fi
    echo "current status: $CLUSTER_STATUS. waiting 20s..."
    sleep 20
done

echo "authenticating Docker to Artifact Registry..."
gcloud auth configure-docker "$REGION-docker.pkg.dev" --quiet

if ! gcloud artifacts repositories describe "$REPO_NAME" --location="$REGION" > /dev/null 2>&1; then
    echo "creating Artifact Registry repository: $REPO_NAME..."
    gcloud artifacts repositories create "$REPO_NAME" --repository-format=docker --location="$REGION" --description="Docker repository for ScrimFinder"
fi

SERVICES="matchmaking-service ranking_service match_history_service detail_filling_service training_service analysis_service"

for SERVICE in $SERVICES; do
    IMAGE_NAME=$(echo "$SERVICE" | tr '_' '-')
    IMAGE_PATH="$REGION-docker.pkg.dev/$PROJECT_ID/$REPO_NAME/$IMAGE_NAME:latest"
    echo "building and pushing $SERVICE..."
    docker buildx build --platform linux/amd64 -t "$IMAGE_PATH" "./$SERVICE" --push
done

echo "fetching GKE credentials..."
gcloud container clusters get-credentials "$CLUSTER_NAME" --region "$REGION" --project "$PROJECT_ID"

echo "applying Kubernetes manifests..."
kubectl apply -f k8s/traefik/crds.yaml
kubectl apply -f k8s/traefik/rbac.yaml
kubectl apply -f k8s/traefik/deployment.yaml
kubectl apply -f k8s/apps/namespace.yaml
kubectl apply -f k8s/apps/infrastructure.yaml

export PROJECT_ID REGION REPO_NAME ENV_TAG CLUSTER_NAME DASHBOARD_USER DASHBOARD_PASSWORD RIOT_API_KEY

if command -v envsubst >/dev/null 2>&1; then
    envsubst < k8s/traefik/middlewares.yaml | kubectl apply -f -
    envsubst < k8s/apps/scrimfinder-apps.yaml | kubectl apply -f -
else
    echo "warning: envsubst not found. applying manifests without variable substitution..."
    kubectl apply -f k8s/traefik/middlewares.yaml
    kubectl apply -f k8s/apps/scrimfinder-apps.yaml
fi
kubectl apply -f k8s/traefik/ingressroutes.yaml

echo "waiting for Traefik LoadBalancer External IP..."
EXTERNAL_IP=""
while [ -z "$EXTERNAL_IP" ]; do
    echo "waiting for IP..."
    EXTERNAL_IP=$(kubectl get svc traefik -n kube-system -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
    [ -z "$EXTERNAL_IP" ] && sleep 15
done

echo "deployment complete! Traefik External IP: $EXTERNAL_IP"
