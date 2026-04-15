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

SERVICES=("matchmaking-service" "ranking-service" "match-history-service" "detail-filling-service" "training-service" "analysis-service")

for SERVICE in "${SERVICES[@]}"; do
    echo "Building and pushing $SERVICE..."
    docker build -t "$REGION-docker.pkg.dev/$PROJECT_ID/$REPO_NAME/$SERVICE:latest" "./$SERVICE"
    docker push "$REGION-docker.pkg.dev/$PROJECT_ID/$REPO_NAME/$SERVICE:latest"
done

echo "Deployment images pushed successfully to $REGION!"
exit 0
