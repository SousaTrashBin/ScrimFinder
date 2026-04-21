#!/bin/bash
set -e

REQUIRED_VARS="SCRIM_PROJECT_ID SCRIM_REGION SCRIM_REPO_NAME SCRIM_CLUSTER_NAME"

for var in $REQUIRED_VARS; do
    if [ "$var" = "" ]; then
        echo "error: missing environment variable $var. please set it in your system environment."
        exit 1
    fi
done

PROJECT_ID="$SCRIM_PROJECT_ID"
REGION="$SCRIM_REGION"
REPO_NAME="$SCRIM_REPO_NAME"
CLUSTER_NAME="$SCRIM_CLUSTER_NAME"

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

if gcloud container clusters describe "$CLUSTER_NAME" --region "${REGION}-a" > /dev/null 2>&1; then
    echo "deleting Kubernetes resources..."
    export PROJECT_ID REGION REPO_NAME CLUSTER_NAME

    SERVICES="detail_filling_service match_history_service"
    POSSIBLE_DATABASES="detail-filling-service-redis match-history-service-postgres"
    DATABASES=""
    has_flag_f=0

    if [ "$#" -gt "0" ]; then
        SERVICES=""
        for ARG in "$@"; do
            if [ "${ARG}" = "-f" ]
            then
                has_flag_f=1
            else
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

    for DATABASE in $DATABASES; do
        delete_resource data/"$DATABASE"
    done

    if [ "${has_flag_f}" -eq 1 ]; then
        delete_resource apps/scrimfinder-namespace
        delete_resource traefik/deployment
        delete_resource traefik/rbac
        delete_resource traefik/crds
        echo "deleting GKE cluster: $CLUSTER_NAME..."
        gcloud container clusters delete "$CLUSTER_NAME" --region "${REGION}-a" --project "$PROJECT_ID" --quiet
    fi
else
    echo "cluster $CLUSTER_NAME not found or already deleted."
fi

echo "shutdown complete!"