#!/bin/bash
set -e

REQUIRED_VARS="SCRIM_PROJECT_ID SCRIM_REGION SCRIM_REPO_NAME SCRIM_ENV_TAG SCRIM_CLUSTER_NAME DASHBOARD_USER DASHBOARD_PASSWORD"

for var in $REQUIRED_VARS; do
    if [ "$var" = "" ]; then
        echo "error: missing environment variable $var. please set it in your system environment."
        exit 1
    fi
done

PROJECT_ID="$SCRIM_PROJECT_ID"
REGION="$SCRIM_REGION"
REPO_NAME="$SCRIM_REPO_NAME"
ENV_TAG="$SCRIM_ENV_TAG"
CLUSTER_NAME="$SCRIM_CLUSTER_NAME"

ZONE="${REGION}-a"

get_cluster_status()
{
    CLUSTER_STATUS=$(gcloud container clusters describe "$CLUSTER_NAME" --zone "${ZONE}" --format="value(status)" 2>/dev/null || echo "NOT_FOUND")
}

echo "setting active GCP project to $PROJECT_ID..."
gcloud config set project "$PROJECT_ID" --quiet

echo "enabling required Google Cloud APIs..."
gcloud services enable \
    artifactregistry.googleapis.com \
    container.googleapis.com \
    compute.googleapis.com \
    iam.googleapis.com

echo "checking GKE cluster status..."
get_cluster_status

if [ "$CLUSTER_STATUS" = "STOPPING" ] || [ "$CLUSTER_STATUS" = "DELETING" ]; then
    echo "cluster is currently being deleted. waiting for deletion to complete..."
    while [ "$CLUSTER_STATUS" != "NOT_FOUND" ]; do
        echo "current status: $CLUSTER_STATUS. waiting 20s..."
        sleep 20
        get_cluster_status
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
    get_cluster_status
    if [ "$CLUSTER_STATUS" = "RUNNING" ]; then
        break
    fi
    echo "current status: $CLUSTER_STATUS. waiting 20s..."
    sleep 20
done

if ! gcloud artifacts repositories describe "$REPO_NAME" --location="$REGION" > /dev/null 2>&1; then
    echo "creating Artifact Registry repository: $REPO_NAME..."
    gcloud artifacts repositories create "$REPO_NAME" --repository-format=docker --location="$REGION" --description="Docker repository for ScrimFinder"
fi

normalize_service_name()
{
    echo "$(echo "$1" | tr '_' '-')"
}

append()
{
    if [ "$2" = "" ]
    then
        echo "$1"
    else
        echo "$2 $1"
    fi
}

grep_words()
{
    RESULT=$3
    for ITEM in $2; do
        if [[ $ITEM =~ ^${1}.* ]]; then
            RESULT="$(append "$ITEM" "$RESULT")"
        fi
    done
    echo "$RESULT"
}

SERVICES="matchmaking-service ranking_service match_history_service detail_filling_service training_service analysis_service"

POSSIBLE_DATABASES="matchmaking-service-postgres matchmaking-service-redis ranking-service-postgres match-history-service-postgres detail-filling-service-redis"
DATABASES=""

POSSIBLE_SECRETS="matchmaking-service-QUARKUS_DATASOURCE_JDBC_URL matchmaking-service-QUARKUS_DATASOURCE_USERNAME matchmaking-service-QUARKUS_DATASOURCE_PASSWORD matchmaking-service-MATCHMAKING_QUARKUS_REDIS_HOSTS"
POSSIBLE_SECRETS="${POSSIBLE_SECRETS} ranking-service-QUARKUS_DATASOURCE_JDBC_URL ranking-service-QUARKUS_DATASOURCE_USERNAME ranking-service-QUARKUS_DATASOURCE_PASSWORD"
POSSIBLE_SECRETS="${POSSIBLE_SECRETS} match-history-service-DB_URL match-history-service-DB_USER match-history-service-DB_PASSWORD"
POSSIBLE_SECRETS="${POSSIBLE_SECRETS} detail-filling-service-RIOT_API_KEY detail-filling-service-DETAIL_FILLING_REDIS_URL detail-filling-service-DETAIL_FILLING_QUARKUS_REDIS_HOSTS"
POSSIBLE_SECRETS="${POSSIBLE_SECRETS} training-service-PLATFORM_DB"
POSSIBLE_SECRETS="${POSSIBLE_SECRETS} analysis-service-PLATFORM_DB"
SECRETS=""

POSTGRES_SECRETS="POSTGRES_USER POSTGRES_PASSWORD"

if [ "$#" -gt "0" ]; then
    SERVICES="$*"
fi

for SERVICE in $SERVICES; do
    SERVICE_NAME=$(normalize_service_name "$SERVICE")
    DATABASES=$(grep_words "$SERVICE_NAME" "$POSSIBLE_DATABASES" "$DATABASES")
done

for SERVICE in $SERVICES; do
    SERVICE_NAME=$(normalize_service_name "$SERVICE")
    SECRETS=$(grep_words "$SERVICE_NAME" "$POSSIBLE_SECRETS" "$SECRETS")
done

for SERVICE in $SERVICES; do
    IMAGE_NAME=$(normalize_service_name "$SERVICE")
    IMAGE_PATH="$REGION-docker.pkg.dev/$PROJECT_ID/$REPO_NAME/$IMAGE_NAME:latest"
    echo "building and pushing $SERVICE..."
    docker buildx build --platform linux/amd64 -t "$IMAGE_PATH" "./$SERVICE" --push
done

echo "fetching GKE credentials..."
gcloud container clusters get-credentials "$CLUSTER_NAME" --zone "$ZONE" --project "$PROJECT_ID"

export PROJECT_ID REGION REPO_NAME ENV_TAG CLUSTER_NAME

echo "generating secrets..."

PREV_SERVICE_NAME="$(normalize_service_name "${SERVICES%% *}")" # First service name
FROM_FILES=()
for SECRET in $SECRETS; do
    SERVICE_NAME="${SECRET%-service*}-service"
    ENV_VAR=${SECRET#"${SERVICE_NAME}-"}
    if [ "${!ENV_VAR}" = "" ]; then
        echo "error: missing environment variable $ENV_VAR. please set it in your system environment."
        exit 1
    fi
    echo -n "${!ENV_VAR}" > "${ENV_VAR}"
    if [ "${PREV_SERVICE_NAME}" = "${SERVICE_NAME}" ]
    then
        FROM_FILES+=("--from-file=./${ENV_VAR}")
    else
        SECRET_K8S_RESOURCE="${PREV_SERVICE_NAME}-secrets"
        kubectl create secret generic "${SECRET_K8S_RESOURCE}" "--namespace=scrimfinder" "${FROM_FILES[@]}" >/dev/null 2>&1 || true
        FROM_FILES=("--from-file=./${ENV_VAR}")
    fi
    PREV_SERVICE_NAME="$SERVICE_NAME"
done
SECRET_K8S_RESOURCE="${PREV_SERVICE_NAME}-secrets"
kubectl create secret generic "${SECRET_K8S_RESOURCE}" "--namespace=scrimfinder" "${FROM_FILES[@]}" >/dev/null 2>&1 || true

FROM_FILES=()
for SECRET in $POSTGRES_SECRETS; do
    if [ "${!SECRET}" = "" ]; then
        echo "error: missing environment variable $SECRET. please set it in your system environment."
        exit 1
    fi
    echo -n "${!SECRET}" > "${SECRET}"
    FROM_FILES+=("--from-file=./${SECRET}")
done
kubectl create secret generic postgres-secrets --namespace=scrimfinder "${FROM_FILES[@]}" >/dev/null 2>&1 || true

echo "applying Kubernetes manifests..."

apply()
{
    kubectl apply -f "k8s/${1}.yaml"
}

apply_with_envsubst()
{
    envsubst < "k8s/${1}.yaml" | kubectl apply -f -
}

if command -v envsubst >/dev/null 2>&1; then
    apply_func=apply_with_envsubst
else
    echo "warning: envsubst not found. applying manifests without variable substitution..."
    apply_func=apply
fi

apply traefik/crds
apply traefik/rbac
apply traefik/deployment
apply apps/scrimfinder-namespace

for DATABASE in $DATABASES; do
    apply data/"$DATABASE"
done
apply data/ai-storage

${apply_func} traefik/middlewares

for SERVICE in $SERVICES; do
    SERVICE_NAME=$(normalize_service_name "$SERVICE")
    ${apply_func} apps/"$SERVICE_NAME"
done

apply traefik/ingressroutes

echo "waiting for Traefik LoadBalancer External IP/Hostname..."
EXTERNAL_IP=""
while [ -z "$EXTERNAL_IP" ]; do
    echo "waiting for IP..."
    EXTERNAL_IP=$(kubectl get svc traefik -n kube-system -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
    if [ -z "$EXTERNAL_IP" ]; then
        echo "checking for Hostname..."
        EXTERNAL_IP=$(kubectl get svc traefik -n kube-system -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "")
    fi
    [ -z "$EXTERNAL_IP" ] && sleep 10
done

echo "deployment complete! Traefik External IP: $EXTERNAL_IP"
