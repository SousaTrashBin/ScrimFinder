#!/bin/bash
set -e

REQUIRED_VARS="SCRIM_PROJECT_ID SCRIM_REGION SCRIM_REPO_NAME SCRIM_ENV_TAG SCRIM_CLUSTER_NAME"

for var in $REQUIRED_VARS; do
    if [ -z "$(eval echo \$$var)" ]; then
        echo "error: $var is not set. please set it in your system environment."
        exit 1
    fi
done

PROJECT_ID="$SCRIM_PROJECT_ID"
REGION="$SCRIM_REGION"
REPO_NAME="$SCRIM_REPO_NAME"
NAMESPACE="scrimfinder"

echo "Ensuring Artifact Registry authentication..."
gcloud auth configure-docker "$REGION-docker.pkg.dev" --quiet

if [ -z "$SERVICES" ]; then
    SERVICES="matchmaking-service ranking_service match_history_service detail_filling_service training_service analysis_service"
fi

IMAGE_REGISTRY="$REGION-docker.pkg.dev/$PROJECT_ID/$REPO_NAME"

echo "Checking services for changes..."

for SERVICE in $SERVICES; do
    IMAGE_NAME=$(echo "$SERVICE" | tr '_' '-')
    
    CHANGES=$(git status --porcelain "./$SERVICE" 2>/dev/null || true)
    if [ -z "$CHANGES" ]; then
        if git diff --quiet HEAD -- "./$SERVICE" 2>/dev/null; then
            echo "[-] No changes detected for $SERVICE. Skipping."
            continue
        fi
    fi

    echo "[+] Changes detected for $SERVICE. Updating..."
    
    GIT_HASH=$(git rev-parse --short HEAD 2>/dev/null || echo "no-git")
    TIMESTAMP=$(date +%s)
    NEW_TAG="v-${GIT_HASH}-${TIMESTAMP}"
    
    IMAGE_PATH="$IMAGE_REGISTRY/$IMAGE_NAME:$NEW_TAG"
    
    echo "    Building and pushing $IMAGE_NAME:$NEW_TAG..."
    docker buildx build --platform linux/amd64 -t "$IMAGE_PATH" "./$SERVICE" --push --quiet
    
    echo "    Searching for deployments with label app=$IMAGE_NAME..."
    DEPLOYMENTS=$(kubectl get deployments -n "$NAMESPACE" -l "app=$IMAGE_NAME" -o name)
    
    if [ -z "$DEPLOYMENTS" ]; then
        echo "    [!] No deployments found with label app=$IMAGE_NAME. Trying to find by name..."
        if kubectl get deployment "$IMAGE_NAME" -n "$NAMESPACE" >/dev/null 2>&1; then
            DEPLOYMENTS="deployment.apps/$IMAGE_NAME"
        else
            echo "[!] No deployments found for $SERVICE with label or name $IMAGE_NAME."
            continue
        fi
    fi

    for DEPLOYMENT in $DEPLOYMENTS; do
        echo "    Updating $DEPLOYMENT..."
        if kubectl set image "$DEPLOYMENT" "$IMAGE_NAME=$IMAGE_PATH" -n "$NAMESPACE"; then
            echo "    Waiting for rollout of $DEPLOYMENT to complete..."
            kubectl rollout status "$DEPLOYMENT" -n "$NAMESPACE"
            echo "[*] $DEPLOYMENT updated successfully to $NEW_TAG."
        else
            echo "[!] Failed to update $DEPLOYMENT."
        fi
    done
done

echo "Update process complete."
