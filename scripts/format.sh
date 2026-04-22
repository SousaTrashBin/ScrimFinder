#!/bin/bash
set -e

echo "formatting ScrimFinder services..."

echo "formatting java services ---"
for service in matchmaking_service ranking_service match_history_service detail_filling_service; do
    if [ -d "$service" ] && [ -f "$service/mvnw" ]; then
        echo "formatting $service..."
        (cd "$service" && ./mvnw spotless:apply -B -q)
    fi
done

echo "formatting python services ---"
if command -v ruff &> /dev/null; then
    RUFF_CMD="ruff"
elif [ -f "./.venv/bin/ruff" ]; then
    RUFF_CMD="./.venv/bin/ruff"
else
    echo "Warning: ruff not found. Skipping Python formatting."
    exit 0
fi

$RUFF_CMD format .
$RUFF_CMD check --fix .

echo "services formatted!"