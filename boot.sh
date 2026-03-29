#!/bin/bash
echo "starting scrimFinder building process (Docker-only mode)..."

if ! command -v docker &> /dev/null; then
    echo "docker not found. Please install Docker."
    exit 1
fi

echo "building and starting system"
docker compose up --build -d

echo "system is booting up!"
echo "traefik API Gateway: http://localhost"
echo "traefik Dashboard: http://localhost:8080"
echo ""
echo "API Documentation (Swagger UI):"
echo "- Matchmaking: http://localhost/api/v1/matchmaking/q/docs"
echo "- Ranking:     http://localhost/api/v1/ranking/q/docs"
echo "- History:     http://localhost/api/v1/history/q/docs"
echo "- Riot Data:   http://localhost/api/v1/riot/q/docs"
echo "- Training:    http://localhost/api/v1/training/q/docs"
echo "- Analysis:    http://localhost/api/v1/analysis/q/docs"
echo ""
echo "Run 'docker compose logs -f' to see progress."
