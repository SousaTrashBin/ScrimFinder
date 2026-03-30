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

export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1

$DOCKER_COMPOSE up --build

echo ""
echo "System has stopped."
