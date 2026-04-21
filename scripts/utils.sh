#!/bin/bash

NAMESPACE="scrimfinder"

case "$1" in
    "db-proxy")
        echo "Starting local proxy to Postgres (localhost:5432)..."
        echo "You can now connect your IDE (IntelliJ, DBeaver) to localhost:5432"
        kubectl port-forward -n $NAMESPACE svc/matchmaking-db 5432:5432
        ;;
    "traefik-proxy")
        echo "Starting local proxy to Traefik Dashboard (localhost:9000)..."
        echo "Open: http://localhost:9000/dashboard/"
        kubectl port-forward -n $NAMESPACE $(kubectl get pods -n $NAMESPACE -l app.kubernetes.io/name=traefik -o name) 9000:9000
        ;;
    "logs")
        if [ -z "$2" ]; then
            echo "Usage: ./utils.sh logs <service-name>"
            exit 1
        fi
        kubectl logs -n $NAMESPACE -l app=$2 -f
        ;;
    "monitor")
        if command -v k9s &> /dev/null; then
            k9s -n $NAMESPACE
        else
            echo "k9s is not installed. Recommended for real-time tracking: https://k9scli.io/"
            echo "Fallback: watching pods..."
            kubectl get pods -n $NAMESPACE -w
        fi
        ;;
    *)
        echo "ScrimFinder Utils"
        echo "Usage: ./utils.sh <command>"
        echo ""
        echo "Commands:"
        echo "  db-proxy      Forward local 5432 to Cloud Postgres"
        echo "  traefik-proxy Forward local 9000 to Traefik Dashboard"
        echo "  logs <svc>    Stream logs for a service"
        echo "  monitor       Open K9s (or kubectl watch)"
        ;;
esac
