#!/bin/bash
set -e

echo "starting ScrimFinder building process..."

if ! command -v docker &> /dev/null; then
    echo "error: docker not found. please install Docker."
    exit 1
fi

if docker compose version &> /dev/null; then
    DOCKER_COMPOSE="docker compose"
elif command -v docker-compose &> /dev/null; then
    DOCKER_COMPOSE="docker-compose"
else
    echo "error: docker compose not found. please install docker compose."
    exit 1
fi

echo "building and starting system using $DOCKER_COMPOSE..."

echo "applying code formatting ..."
for service in matchmaking_service ranking_service match_history_service detail_filling_service; do
    if [ -f "$service/mvnw" ]; then
        echo "formatting $service..."
        (cd "$service" && ./mvnw spotless:apply -B -q)
    fi
done

export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1

$DOCKER_COMPOSE up --build

echo ""
echo "System has stopped."
