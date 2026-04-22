#!/bin/bash
set -e

REQUIRED_VARS="SCRIM_PROJECT_ID SCRIM_REGION SCRIM_REPO_NAME SCRIM_ENV_TAG SCRIM_CLUSTER_NAME"

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

echo "setting active GCP project to $PROJECT_ID..."
gcloud config set project "$PROJECT_ID" --quiet

delete_resource()
{
    kubectl delete -f "k8s/${1}.yaml" --ignore-not-found || true
}

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

if gcloud container clusters describe "$CLUSTER_NAME" --zone "$ZONE" > /dev/null 2>&1; then
    echo "fetching GKE credentials..."
    gcloud container clusters get-credentials "$CLUSTER_NAME" --zone "$ZONE" --project "$PROJECT_ID"

    echo "deleting Kubernetes resources..."
    export PROJECT_ID REGION REPO_NAME CLUSTER_NAME

    SERVICES="matchmaking-service ranking_service match_history_service detail_filling_service training_service analysis_service"
    POSSIBLE_DATABASES="matchmaking-service-postgres matchmaking-service-redis ranking-service-postgres match-history-service-postgres detail-filling-service-redis"
    DATABASES=""
    has_flag_f=0
    has_services=0

    if [ "$#" -gt "0" ]; then
        for ARG in "$@"; do
            if [ "${ARG}" = "-f" ]
            then
                has_flag_f=1
            else
                if [ "${has_services}" -eq 0 ]; then
                    SERVICES=""
                fi
                has_services=1
                SERVICES=$(append "${ARG}" "${SERVICES}")
            fi
        done
    fi

    for SERVICE in $SERVICES; do
        SERVICE_NAME=$(normalize_service_name "$SERVICE")
        DATABASES=$(grep_words "$SERVICE_NAME" "$POSSIBLE_DATABASES" "$DATABASES")
    done

    if [ "${has_flag_f}" -eq 1 ]; then
        delete_resource traefik/ingressroutes
    fi

    for SERVICE in $SERVICES; do
        SERVICE_NAME=$(normalize_service_name "$SERVICE")
        delete_resource apps/"$SERVICE_NAME"
    done

    if [ "${has_flag_f}" -eq 1 ]; then
        delete_resource traefik/middlewares
    fi

    delete_resource data/ai-storage
    for DATABASE in $DATABASES; do
        delete_resource data/"$DATABASE"
    done

    if [ "${has_flag_f}" -eq 1 ]; then
        delete_resource apps/scrimfinder-namespace
        delete_resource traefik/deployment
        delete_resource traefik/rbac
        delete_resource traefik/crds

        echo "deleting GKE cluster: $CLUSTER_NAME..."
        gcloud container clusters delete "$CLUSTER_NAME" --zone "$ZONE" --project "$PROJECT_ID" --quiet
    fi
else
    echo "cluster $CLUSTER_NAME not found or already deleted."
fi

echo "shutdown complete!"