#!/bin/bash
set -e

REQUIRED_VARS=("SCRIM_PROJECT_ID" "SCRIM_REGION" "SCRIM_REPO_NAME" "SCRIM_ENV_TAG" "SCRIM_CLUSTER_NAME")
for var in "${REQUIRED_VARS[@]}"; do
    if [ -z "${!var}" ]; then
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

if gcloud container clusters describe "$CLUSTER_NAME" --region "$REGION" > /dev/null 2>&1; then
    echo "fetching GKE credentials..."
    gcloud container clusters get-credentials "$CLUSTER_NAME" --region "$REGION" --project "$PROJECT_ID"

    echo "deleting Kubernetes resources (attempting graceful cleanup)..."
    export PROJECT_ID REGION REPO_NAME ENV_TAG CLUSTER_NAME
    
    kubectl delete -f k8s/traefik/ingressroutes.yaml --ignore-not-found || true
    kubectl delete -f k8s/traefik/middlewares.yaml --ignore-not-found || true
    
    if command -v envsubst >/dev/null 2>&1; then
        envsubst < k8s/apps/scrimfinder-apps.yaml | kubectl delete -f - --ignore-not-found || true
    else
        kubectl delete -f k8s/apps/scrimfinder-apps.yaml --ignore-not-found || true
    fi
    
    kubectl delete -f k8s/traefik/deployment.yaml --ignore-not-found || true
    kubectl delete -f k8s/apps/infrastructure.yaml --ignore-not-found || true
    kubectl delete -f k8s/traefik/rbac.yaml --ignore-not-found || true
    
    echo "deleting GKE cluster: $CLUSTER_NAME to stop all compute costs..."
    gcloud container clusters delete "$CLUSTER_NAME" --region "$REGION" --project "$PROJECT_ID" --quiet
else
    echo "cluster $CLUSTER_NAME not found or already deleted."
fi

echo "shutdown complete! GKE cluster and computing resources have been removed."