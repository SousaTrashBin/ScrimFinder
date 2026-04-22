#!/bin/bash
set -e

REQUIRED_VARS="SCRIM_PROJECT_ID SCRIM_REGION SCRIM_REPO_NAME SCRIM_ENV_TAG SCRIM_CLUSTER_NAME RIOT_API_KEY SCRIM_DB_USER SCRIM_DB_PASSWORD"

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

if ! kubectl cluster-info > /dev/null 2>&1; then
    echo "checking GKE cluster status..."
    CLUSTER_STATUS=$(gcloud container clusters describe "$CLUSTER_NAME" --zone "$ZONE" --format="value(status)" 2>/dev/null || echo "NOT_FOUND")

    if [ "$CLUSTER_STATUS" = "STOPPING" ] || [ "$CLUSTER_STATUS" = "DELETING" ]; then
        echo "cluster is currently being deleted. waiting for deletion to complete..."
        while [ "$CLUSTER_STATUS" != "NOT_FOUND" ]; do
            echo "current status: $CLUSTER_STATUS. waiting 20s..."
            sleep 20
            CLUSTER_STATUS=$(gcloud container clusters describe "$CLUSTER_NAME" --zone "$ZONE" --format="value(status)" 2>/dev/null || echo "NOT_FOUND")
        done
    fi

    if [ "$CLUSTER_STATUS" = "NOT_FOUND" ]; then
        echo "creating GKE zonal cluster: $CLUSTER_NAME in $ZONE ..."
        gcloud container clusters create "$CLUSTER_NAME" \
            --zone "$ZONE" \
            --num-nodes 1 \
            --machine-type e2-standard-4 \
            --disk-size 40 \
            --disk-type pd-standard \
            --spot \
            --enable-autoscaling --min-nodes 1 --max-nodes 1 \
            --quiet
        CLUSTER_STATUS="PROVISIONING"
    fi

    echo "ensuring cluster $CLUSTER_NAME is RUNNING..."
    until [ "$CLUSTER_STATUS" = "RUNNING" ]; do
        CLUSTER_STATUS=$(gcloud container clusters describe "$CLUSTER_NAME" --zone "$ZONE" --project "$PROJECT_ID" --format="value(status)" 2>/dev/null || echo "ERROR")
        if [ "$CLUSTER_STATUS" = "RUNNING" ]; then
            break
        fi
        echo "current status: $CLUSTER_STATUS. waiting 20s..."
        sleep 20
    done
    gcloud container clusters get-credentials "$CLUSTER_NAME" --zone "$ZONE" --project "$PROJECT_ID"
else
    echo "already connected to GKE. skipping status checks."
fi

echo "authenticating Docker to Artifact Registry..."
gcloud auth configure-docker "$REGION-docker.pkg.dev" --quiet

if ! gcloud artifacts repositories describe "$REPO_NAME" --location="$REGION" > /dev/null 2>&1; then
    echo "creating Artifact Registry repository: $REPO_NAME..."
    gcloud artifacts repositories create "$REPO_NAME" --repository-format=docker --location="$REGION" --description="Docker repository for ScrimFinder"
fi

if [ -z "$SERVICES" ]; then
    SERVICES="matchmaking-service ranking_service match_history_service detail_filling_service training_service analysis_service"
fi

if [ "$SKIP_BUILD" != "true" ]; then
    echo "building and pushing services in parallel..."
    for SERVICE in $SERVICES; do
        (
            IMAGE_NAME=$(echo "$SERVICE" | tr '_' '-')
            IMAGE_PATH="$REGION-docker.pkg.dev/$PROJECT_ID/$REPO_NAME/$IMAGE_NAME:latest"
            echo "building $SERVICE..."
            docker buildx build --platform linux/amd64 -t "$IMAGE_PATH" "./$SERVICE" --push --quiet
            echo "finished $SERVICE."
        ) &
    done
    wait
    echo "all builds complete."
else
    echo "skipping build phase."
fi

echo "deploying with Helm (including Traefik and Routing)..."
helm dependency update k8s/charts/scrimfinder

echo "preparing namespace..."
kubectl create namespace scrimfinder --dry-run=client -o yaml | kubectl apply -f -

echo "installing/updating Traefik CRDs..."
kubectl apply -f https://raw.githubusercontent.com/traefik/traefik/v3.0/docs/content/reference/dynamic-configuration/kubernetes-crd-definition-v1.yml

if helm plugin list | grep -q "diff"; then
    echo "--- PREVIEWING CHANGES (helm diff) ---"
    helm diff upgrade scrimfinder k8s/charts/scrimfinder \
        --namespace scrimfinder \
        --set global.imageRegistry="${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO_NAME}" \
        --set global.region="${REGION}" \
        --set global.projectId="${PROJECT_ID}" \
        --set global.repoName="${REPO_NAME}" \
        --set secrets.riotApiKey="$RIOT_API_KEY" \
        --set secrets.dbUser="$SCRIM_DB_USER" \
        --set secrets.dbPassword="$SCRIM_DB_PASSWORD" \
        --allow-unreleased \

    if [ "$CONFIRM_DEPLOY" = "true" ]; then
        echo "Proceeding with deployment..."
    else
        echo "Check the diff above. To bypass this check in the future, set CONFIRM_DEPLOY=true."
    fi
fi

echo "deploying with Helm..."
helm upgrade --install scrimfinder k8s/charts/scrimfinder \
    --namespace scrimfinder --create-namespace \
    --set global.imageRegistry="${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO_NAME}" \
    --set global.region="${REGION}" \
    --set global.projectId="${PROJECT_ID}" \
    --set global.repoName="${REPO_NAME}" \
    --set secrets.riotApiKey="$RIOT_API_KEY" \
    --set secrets.dbUser="$SCRIM_DB_USER" \
    --set secrets.dbPassword="$SCRIM_DB_PASSWORD" \
    --force-conflicts \
    --wait \
    --timeout 10m

echo "waiting for Traefik LoadBalancer External IP/Hostname..."
EXTERNAL_IP=""
while [ -z "$EXTERNAL_IP" ]; do
    echo "waiting for IP..."
    EXTERNAL_IP=$(kubectl get svc scrimfinder-traefik -n scrimfinder -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
    if [ -z "$EXTERNAL_IP" ]; then
        echo "checking for Hostname..."
        EXTERNAL_IP=$(kubectl get svc scrimfinder-traefik -n scrimfinder -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "")
    fi
    [ -z "$EXTERNAL_IP" ] && sleep 10
done

echo "deployment complete! Traefik External IP: $EXTERNAL_IP"

echo "verifying service health with internal smoke tests..."
helm test scrimfinder --namespace scrimfinder
