#!/usr/bin/env bash
set -euo pipefail

profile="${1:-fast-tests}"
services="${SERVICES:-matchmaking-service ranking_service match_history_service detail_filling_service}"

for service in $services; do
  echo "running $profile for $service"
  (cd "$service" && ./mvnw -B -q "-P${profile}" test)
done
