#!/usr/bin/env bash
set -euo pipefail

if [ -z "${BASE_URL:-${SCRIM_SYSTEM_BASE_URL:-}}" ]; then
  echo "error: BASE_URL or SCRIM_SYSTEM_BASE_URL is required for system tests"
  exit 1
fi

export BASE_URL="${BASE_URL:-$SCRIM_SYSTEM_BASE_URL}"

services="${SERVICES:-matchmaking-service}"

for service in $services; do
  echo "running system tests for $service against $BASE_URL"
  cd "$service"
  ./mvnw -B -q -Psystem-tests -DsystemTests=true test
  cd ..
done
