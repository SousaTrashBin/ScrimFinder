#!/bin/bash
set -e

# Configuration
PROJECT_ID="scrimfinder-12345"
REGION="europe-west3"
REPO_NAME="scrimfinder"
ENV_TAG="development"

echo "Setting active GCP project to $PROJECT_ID..."
gcloud config set project "$PROJECT_ID"

echo "Enabling required Google Cloud APIs..."
gcloud services enable artifactregistry.googleapis.com container.googleapis.com compute.googleapis.com

echo "Tagging project with environment=$ENV_TAG..."
# Try updating labels first (requires alpha component)
if gcloud alpha projects update "$PROJECT_ID" --update-labels="environment=$ENV_TAG" > /dev/null 2>&1; then
    echo "Labels updated successfully."
else
    echo "Could not update labels using 'gcloud alpha'. This is usually fine if you are on a personal account."
fi

echo "Authenticating Docker to Artifact Registry..."
gcloud auth configure-docker "$REGION-docker.pkg.dev" --quiet

# Check if repository already exists
if ! gcloud artifacts repositories describe "$REPO_NAME" --location="$REGION" > /dev/null 2>&1; then
    echo "Creating Artifact Registry repository: $REPO_NAME..."
    gcloud artifacts repositories create "$REPO_NAME" --repository-format=docker --location="$REGION" --description="Docker repository for ScrimFinder"
else
    echo "Repository $REPO_NAME already exists."
fi

SERVICES=("matchmaking-service" "ranking_service" "match_history_service" "detail_filling_service" "training_service" "analysis_service")

for SERVICE in "${SERVICES[@]}"; do
    # Image name should use hyphens to match k8s manifests
    IMAGE_NAME=$(echo "$SERVICE" | tr '_' '-')
    echo "Building and pushing $SERVICE (Tag: $IMAGE_NAME)..."
    docker build -t "$REGION-docker.pkg.dev/$PROJECT_ID/$REPO_NAME/$IMAGE_NAME:latest" "./$SERVICE"
    docker push "$REGION-docker.pkg.dev/$PROJECT_ID/$REPO_NAME/$IMAGE_NAME:latest"
done

echo "Deployment images pushed successfully to $REGION!"
exit 0
