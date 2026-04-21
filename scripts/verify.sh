#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$ROOT_DIR"
if [ -f "test.sh" ]; then
    echo "running local tests..."
    bash "test.sh"
else
    echo "error: test.sh not found in root directory!"
    exit 1
fi

echo "deploying changes..."
bash "$SCRIPT_DIR/deploy.sh"

echo "waiting for Traefik External IP..."
EXTERNAL_IP=""
MAX_RETRIES=30
RETRY_COUNT=0

while [ -z "$EXTERNAL_IP" ] && [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    EXTERNAL_IP=$(kubectl get svc scrimfinder-traefik -n scrimfinder -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
    if [ -z "$EXTERNAL_IP" ]; then
        EXTERNAL_IP=$(kubectl get svc scrimfinder-traefik -n scrimfinder -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "")
    fi
    if [ -z "$EXTERNAL_IP" ]; then
        echo "waiting for IP/Hostname ($((RETRY_COUNT+1))/$MAX_RETRIES)..."
        sleep 10
        RETRY_COUNT=$((RETRY_COUNT+1))
    fi
done

if [ -z "$EXTERNAL_IP" ]; then
    echo "error: could not retrieve Traefik External IP."
    exit 1
fi

echo "traefik is accessible at: $EXTERNAL_IP"

SERVICES_TO_CHECK="
matchmaking-service:/api/v1/matchmaking/q/health/ready
ranking-service:/api/v1/ranking/q/health/ready
match-history-service:/api/v1/history/q/health/ready
detail-filling-service:/api/v1/riot/q/health/ready
training-service:/api/v1/training/q/health/ready
analysis-service:/api/v1/analysis/q/health/ready
"

check_health() {
    local SERVICE_NAME=$1
    local HEALTH_PATH=$2
    local IP=$3
    local READY=false

    echo "verifying $SERVICE_NAME readiness (http://$IP$HEALTH_PATH)..."
    
    for i in {1..25}; do
        RESPONSE=$(curl -s "http://$IP$HEALTH_PATH" || echo "FAILED")
        if echo "$RESPONSE" | grep -qE '"status":\s*"(UP|ok)"'; then
            READY=true
            break
        fi
        sleep 5
    done

    if [ "$READY" = "true" ]; then
        echo "SUCCESS! $SERVICE_NAME is UP."
        return 0
    else
        echo "FAILURE! $SERVICE_NAME did not report 'UP' or 'ok' within the timeout."
        echo "last response from $SERVICE_NAME: $RESPONSE"
        echo "--- FETCHING LAST 20 LINES OF LOGS FOR $SERVICE_NAME ---"
        kubectl logs -n scrimfinder -l app=$SERVICE_NAME --tail=20 || echo "could not fetch logs."
        return 1
    fi
}

PIDS=()
for ENTRY in $SERVICES_TO_CHECK; do
    SERVICE_NAME=$(echo $ENTRY | cut -d':' -f1)
    HEALTH_PATH=$(echo $ENTRY | cut -d':' -f2)
    check_health "$SERVICE_NAME" "$HEALTH_PATH" "$EXTERNAL_IP" &
    PIDS+=($!)
done

FAILED=0
for pid in "${PIDS[@]}"; do
    if ! wait "$pid"; then
        FAILED=1
    fi
done

if [ $FAILED -ne 0 ]; then
    echo "verification failed for one or more services."
    exit 1
fi

echo "all services verified successfully!"

echo "verification complete!"
