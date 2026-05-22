#!/usr/bin/env bash
set -euo pipefail

if [ -z "${BASE_URL:-${SCRIM_SYSTEM_BASE_URL:-}}" ]; then
  echo "error: BASE_URL or SCRIM_SYSTEM_BASE_URL is required for system tests"
  exit 1
fi

export BASE_URL="${BASE_URL:-$SCRIM_SYSTEM_BASE_URL}"

echo "running system tests for matchmaking-service against $BASE_URL"
(cd matchmaking-service && ./mvnw -B -q -Psystem-tests -DsystemTests=true test)
