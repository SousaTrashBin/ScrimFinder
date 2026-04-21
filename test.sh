#!/bin/bash
set -e

if command -v pytest &> /dev/null; then
    PYTEST_CMD="pytest"
elif [ -f "./.venv/bin/pytest" ]; then
    PYTEST_CMD="./.venv/bin/pytest"
else
    echo "warning: pytest not found."
fi

echo "running ScrimFinder services tests..."

echo "testing java services"
for service in matchmaking-service ranking_service match_history_service detail_filling_service; do
    if [ -d "$service" ]; then
        echo "testing service: $service..."
        (cd "$service" && ./mvnw test -B)
    fi
done

echo "testing python services"
if [ -n "$PYTEST_CMD" ]; then
    for service in analysis_service training_service; do
        if [ -d "$service" ]; then
            echo "testing service: $service..."
            PYTHONPATH="$(pwd)/$service:$PYTHONPATH" $PYTEST_CMD "$service"
        fi
    done
else
    echo "skipping python tests (pytest not found)."
fi

echo "tests completed!"
