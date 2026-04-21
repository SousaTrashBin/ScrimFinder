#!/bin/bash

set -e

if [ "${#}" -lt 1 ] || [ "${#}" -gt 2 ]; then
    echo "invalid number of arguments. expected 1 or 2"
    echo "usage: ./rollback.sh <type/name> [<revision_number>]"
    exit 1
fi

REQUIRED_VARS="SCRIM_PROJECT_ID SCRIM_REGION SCRIM_CLUSTER_NAME"

for var in $REQUIRED_VARS; do
    if [ "$var" = "" ]; then
        echo "error: missing environment variable $var. please set it in your system environment."
        exit 1
    fi
done

PROJECT_ID="$SCRIM_PROJECT_ID"
REGION="$SCRIM_REGION"
CLUSTER_NAME="$SCRIM_CLUSTER_NAME"

get_cluster_status()
{
    CLUSTER_STATUS=$(gcloud container clusters describe "$CLUSTER_NAME" --region "${REGION}-a" --format="value(status)" 2>/dev/null || echo "NOT_FOUND")
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
    echo "cluster is currently being deleted. no reason to rollout an update"
    exit 1
fi

if [ "$CLUSTER_STATUS" = "NOT_FOUND" ]; then
    echo "cluster not found: ${CLUSTER_NAME}"
    exit 1
fi

RESOURCE="${1}"
REVISION="${2}"

if [ -z "${REVISION}" ]; then
    echo
    kubectl rollout history "${RESOURCE}"
    echo -n "please type the revision number to rollback to or quit to abort: "
    read -r REVISION
    if [ "${REVISION}" = "quit" ]; then
        exit 0
    fi
fi

echo "rolling back resource ${RESOURCE} to revision ${REVISION}"
kubectl rollout undo "${RESOURCE}" "--to-revision=${REVISION}"