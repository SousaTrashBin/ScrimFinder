#!/bin/bash
echo "starting scrimFinder building process..."

QUARKUS_SERVICES=("matchmaking_service" "ranking_service" "match_history_service" "detail_filling_service")

for service in "${QUARKUS_SERVICES[@]}"; do
    echo "--- Building Quarkus Service: $service ---"
    cd "$service" || exit
    ./mvnw spotless:apply
    ./mvnw package -DskipTests
    cd ..
done

echo "setting up Python environment with uv"

if ! command -v uv &> /dev/null; then
    echo "uv not found. Please install it: https://github.com/astral-sh/uv"
    exit 1
fi

if [ ! -d ".venv" ]; then
    echo "creating virtual environment..."
    uv venv
fi

echo "installing dependencies and linting tools..."
uv pip install ruff pytest pytest-asyncio
if [ -f "analysis_service/requirements.txt" ]; then uv pip install -r analysis_service/requirements.txt; fi
if [ -f "training_service/requirements.txt" ]; then uv pip install -r training_service/requirements.txt; fi

echo "running ruff linting and formatting check..."
if ! uv run ruff check .; then
    echo "::error::Ruff linting failed! Fix errors or run 'uv run ruff check --fix .'"
    exit 1
fi

if ! uv run ruff format --check .; then
    echo "::error::Ruff formatting check failed! Running auto-format..."
    uv run ruff format .
fi

echo "starting docker compose"
docker compose up --build -d

echo "system is booting up!"
echo "traefik API Gateway: http://localhost"
echo "traefik Dashboard: http://localhost:8080"
echo ""
echo "API Endpoints:"
echo "- Matchmaking: http://localhost/api/v1/matchmaking"
echo "- Ranking: http://localhost/api/v1/ranking"
echo "- History: http://localhost/api/v1/history"
echo "- Detail Filling: http://localhost/api/v1/riot"
echo "- Training: http://localhost/api/v1/training"
echo "- Analysis: http://localhost/api/v1/analysis"
echo ""
echo "Run 'docker compose logs -f' to see progress."
