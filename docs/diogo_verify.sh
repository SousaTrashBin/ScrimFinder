#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

bash "$SCRIPT_DIR/diogo_shutdown.sh"

cd "$ROOT_DIR"
if [ -f "test.sh" ]; then
    bash "test.sh"
else
    echo "error: test.sh not found in root directory!"
    exit 1
fi

bash "$SCRIPT_DIR/diogo_deploy.sh"


echo "waiting for Traefik External IP for verification..."
EXTERNAL_IP=""
MAX_RETRIES=12
RETRY_COUNT=0

while [ -z "$EXTERNAL_IP" ] && [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    EXTERNAL_IP=$(kubectl get svc traefik -n kube-system -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
    if [ -z "$EXTERNAL_IP" ]; then
        echo "waiting for IP ($((RETRY_COUNT+1))/$MAX_RETRIES)..."
        sleep 10
        RETRY_COUNT=$((RETRY_COUNT+1))
    fi
done

if [ -z "$EXTERNAL_IP" ]; then
    echo "could not retrieve Traefik External IP after waiting."
    exit 1
fi

echo "verifying Matchmaking Service endpoint (http://$EXTERNAL_IP/api/v1/matchmaking/q/health/ready)..."

RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" "http://$EXTERNAL_IP/api/v1/matchmaking/q/health/ready")

if [ "$RESPONSE" -eq 200 ]; then
    echo "success! Service is READY. HTTP Status: $RESPONSE"
else
    echo "warning: Expected 200 but got $RESPONSE. It might still be starting up or middleware isn't ready."
    echo "retrying in 10 seconds..."
    sleep 10
    RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" "http://$EXTERNAL_IP/api/v1/matchmaking/q/health/ready")
    if [ "$RESPONSE" -eq 200 ]; then
        echo "success on retry! HTTP Status: $RESPONSE"
    else
        echo "failure! Service is not ready. HTTP Status: $RESPONSE"
        exit 1
    fi
fi

echo "verification complete!"
