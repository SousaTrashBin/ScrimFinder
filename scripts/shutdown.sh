#!/bin/bash
set -euo pipefail

REQUIRED_VARS="SCRIM_PROJECT_ID SCRIM_REGION SCRIM_REPO_NAME SCRIM_ENV_TAG SCRIM_CLUSTER_NAME"

for var in $REQUIRED_VARS; do
    if [ -z "$(eval echo \${$var:-})" ]; then
        echo "error: $var is not set. please set it in your system environment."
        exit 1
    fi
done

PROJECT_ID="$SCRIM_PROJECT_ID"
REGION="$SCRIM_REGION"
REPO_NAME="$SCRIM_REPO_NAME"
ENV_TAG="$SCRIM_ENV_TAG"
CLUSTER_NAME="$SCRIM_CLUSTER_NAME"
SCRIM_NAMESPACE="${SCRIM_NAMESPACE:-scrimfinder}"
ZONE="${REGION}-a"

DELETE_ARTIFACT_REPO="${SCRIM_DELETE_ARTIFACT_REPO:-false}"
DELETE_UNUSED_K8S_IPS="${SCRIM_DELETE_UNUSED_K8S_IPS:-true}"
DELETE_ORPHAN_PVC_DISKS="${SCRIM_DELETE_ORPHAN_PVC_DISKS:-false}"

echo "setting active GCP project to $PROJECT_ID..."
gcloud config set project "$PROJECT_ID" --quiet

cleanup_k8s_resources() {
    echo "attempting Helm uninstall..."
    helm uninstall scrimfinder -n "$SCRIM_NAMESPACE" --wait || true

    echo "deleting namespace $SCRIM_NAMESPACE (if present)..."
    kubectl delete namespace "$SCRIM_NAMESPACE" --ignore-not-found=true --wait=true --timeout=5m || true
}

if gcloud container clusters describe "$CLUSTER_NAME" --zone "$ZONE" --project "$PROJECT_ID" >/dev/null 2>&1; then
    echo "fetching GKE credentials..."
    gcloud container clusters get-credentials "$CLUSTER_NAME" --zone "$ZONE" --project "$PROJECT_ID"

    cleanup_k8s_resources

    echo "deleting GKE cluster: $CLUSTER_NAME..."
    gcloud container clusters delete "$CLUSTER_NAME" --zone "$ZONE" --project "$PROJECT_ID" --quiet
else
    echo "cluster $CLUSTER_NAME not found or already deleted."
fi

if [ "$DELETE_UNUSED_K8S_IPS" = "true" ]; then
    echo "cleaning unused regional static IPs likely created by Kubernetes load balancers..."

    mapfile -t CANDIDATE_IPS < <(gcloud compute addresses list \
        --project "$PROJECT_ID" \
        --filter="region:($REGION) AND status=RESERVED" \
        --format="csv[no-heading](name,users)" | awk -F',' '
            $2 == "" && $1 ~ /^(k8s-|scrimfinder|traefik)/ { print $1 }
        ' || true)

    if [ ${#CANDIDATE_IPS[@]} -gt 0 ]; then
        for ip_name in "${CANDIDATE_IPS[@]}"; do
            echo "deleting unused IP: $ip_name"
            gcloud compute addresses delete "$ip_name" --region "$REGION" --project "$PROJECT_ID" --quiet || true
        done
    else
        echo "no obvious unused Kubernetes-related static IPs found in $REGION."
    fi
fi

if [ "$DELETE_ORPHAN_PVC_DISKS" = "true" ]; then
    echo "cleaning orphan PersistentVolume disks (name prefix: pvc-)..."

    while read -r disk_name disk_zone; do
        [ -z "$disk_name" ] && continue
        echo "deleting orphan disk: $disk_name ($disk_zone)"
        gcloud compute disks delete "$disk_name" \
            --zone "$disk_zone" \
            --project "$PROJECT_ID" \
            --quiet || true
    done < <(gcloud compute disks list \
        --project "$PROJECT_ID" \
        --filter="name~'^pvc-'" \
        --format="value(name,zone)")
fi

if [ "$DELETE_ARTIFACT_REPO" = "true" ]; then
    if gcloud artifacts repositories describe "$REPO_NAME" --location "$REGION" --project "$PROJECT_ID" >/dev/null 2>&1; then
        echo "deleting Artifact Registry repository $REPO_NAME..."
        gcloud artifacts repositories delete "$REPO_NAME" \
            --location "$REGION" \
            --project "$PROJECT_ID" \
            --quiet
    else
        echo "Artifact Registry repository $REPO_NAME not found."
    fi
else
    echo "keeping Artifact Registry repository (set SCRIM_DELETE_ARTIFACT_REPO=true to remove image storage costs)."
fi

echo "shutdown complete!"
