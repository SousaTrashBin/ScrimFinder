#!/bin/bash
set -e

REQUIRED_VARS=("SCRIM_PROJECT_ID" "SCRIM_REGION" "SCRIM_CLUSTER_NAME")
for var in "${REQUIRED_VARS[@]}"; do
    if [ -z "${!var}" ]; then
        echo "error: $var is not set. please set it in your system environment."
        exit 1
    fi
done

PROJECT_ID="$SCRIM_PROJECT_ID"
REGION="$SCRIM_REGION"
CLUSTER_NAME="$SCRIM_CLUSTER_NAME"

echo "setting active GCP project to $PROJECT_ID..."
gcloud config set project "$PROJECT_ID" --quiet

echo "fetching GKE credentials (if cluster exists)..."
gcloud container clusters get-credentials "$CLUSTER_NAME" --region "$REGION" --project "$PROJECT_ID" || echo "cluster not found or not accessible, skipping kubectl deletion."

echo "deleting Kubernetes resources..."
kubectl delete -f k8s/traefik/ingressroutes.yaml --ignore-not-found || true
kubectl delete -f k8s/traefik/middlewares.yaml --ignore-not-found || true
kubectl delete -f k8s/apps/scrimfinder-apps.yaml --ignore-not-found || true
kubectl delete -f k8s/traefik/deployment.yaml --ignore-not-found || true
kubectl delete -f k8s/apps/infrastructure.yaml --ignore-not-found || true
kubectl delete -f k8s/traefik/rbac.yaml --ignore-not-found || true
kubectl delete -f k8s/traefik/crds.yaml --ignore-not-found || true

echo "shutdown complete!"
