#!/bin/bash
echo "starting scrimFinder building process..."

SERVICES=("matchmaking_service" "ranking_service" "match_history_service" "detail_filling_service")

for service in "${SERVICES[@]}"; do
    echo "building $service..."
    cd "$service" || exit
    ./mvnw package -DskipTests
    cd ..
done

echo "starting docker compose..."
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
echo ""
echo "Run 'docker compose logs -f' to see progress."
