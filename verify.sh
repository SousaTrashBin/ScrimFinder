#!/bin/bash
set -e

if [ -f "test.sh" ]; then
    echo "running local tests..."
    bash "test.sh"
else
    echo "error: test.sh not found in root directory!"
    exit 1
fi

echo "deploying changes..."
bash "./deploy.sh"

echo "waiting for Traefik External IP..."
EXTERNAL_IP=""
MAX_RETRIES=20
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
    echo "error: could not retrieve Traefik External IP."
    exit 1
fi

echo "verifying Matchmaking Service readiness (http://$EXTERNAL_IP/api/v1/matchmaking/q/health/ready)..."

READY=false
for i in {1..15}; do
    RESPONSE=$(curl -s "http://$EXTERNAL_IP/api/v1/matchmaking/q/health/ready" || echo "FAILED")
    if echo "$RESPONSE" | grep -q '"status": "UP"'; then
        READY=true
        break
    fi
    echo "waiting for service to report 'UP'... ($i/15)"
    sleep 5
done

if [ "$READY" = "true" ]; then
    echo "SUCCESS! Service is UP and responding correctly."
else
    echo "FAILURE! Service did not report 'UP' within the timeout."
    echo "last response: $RESPONSE"
    exit 1
fi

echo "verification complete!"
