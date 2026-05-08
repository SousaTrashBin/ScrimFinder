#!/bin/bash

NAMESPACE="scrimfinder"

check_helm_plugins() {
    local plugins=("diff")
    for plugin in "${plugins[@]}"; do
        if ! helm plugin list | grep -q "$plugin"; then
            echo "Warning: Helm plugin '$plugin' is not installed. Some features may be disabled."
        fi
    done
}

case "$1" in
    "diff")
        echo "Calculating Helm diff for scrimfinder..."
        helm diff upgrade scrimfinder k8s/charts/scrimfinder \
            --namespace $NAMESPACE \
            --set global.microservicesRegistry="${SCRIM_REGION}-docker.pkg.dev/${SCRIM_PROJECT_ID}/${SCRIM_REPO_NAME}" \
            --set global.region="${SCRIM_REGION}" \
            --set global.projectId="${SCRIM_PROJECT_ID}" \
            --set global.repoName="${SCRIM_REPO_NAME}" \
            --set secrets.riotApiKey="$RIOT_API_KEY" \
            --set secrets.dbUser="$SCRIM_DB_USER" \
            --set secrets.dbPassword="$SCRIM_DB_PASSWORD" \
            --set secrets.redisPassword="${SCRIM_REDIS_PASSWORD:-redispassword}" \
            --set secrets.rabbitmqUser="${SCRIM_RABBITMQ_USER:-user}" \
            --set secrets.rabbitmqPassword="${SCRIM_RABBITMQ_PASSWORD:-rabbitmqpassword}" \
            --set secrets.rabbitmqErlangCookie="${SCRIM_RABBITMQ_ERLANG_COOKIE:-erlangcookie}" \
            --set global.rabbitmqHost="${SCRIM_RABBITMQ_HOST:-scrimfinder-rabbitmq-broker}" \
            --set global.rabbitmqPort="${SCRIM_RABBITMQ_PORT:-5672}" \
            --allow-unreleased
        ;;
    "db-proxy")
        DB_SVC="${2:-matchmaking-db}"
        PORT="${3:-5432}"
        echo "Starting local proxy to $DB_SVC (localhost:$PORT)..."
        echo "You can now connect your IDE to localhost:$PORT"
        kubectl port-forward -n $NAMESPACE svc/$DB_SVC $PORT:5432
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
        echo "  db-proxy [s]  Forward local 5432 to DB service (default: matchmaking-db)"
        echo "  traefik-proxy Forward local 9000 to Traefik Dashboard"
        echo "  logs <svc>    Stream logs for a service"
        echo "  monitor       Open K9s (or kubectl watch)"
        ;;
esac
