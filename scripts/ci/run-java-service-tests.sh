#!/usr/bin/env bash
set -euo pipefail

profile="${1:-fast-tests}"
services="${SERVICES:-matchmaking-service ranking_service match_history_service detail_filling_service}"

export DB_USER="${DB_USER:-${SCRIM_DB_USER:-postgres}}"
export DB_PASSWORD="${DB_PASSWORD:-${SCRIM_DB_PASSWORD:-postgres}}"
export REDIS_PASSWORD="${REDIS_PASSWORD:-${SCRIM_REDIS_PASSWORD:-redispassword}}"
export QUARKUS_REDIS_PASSWORD="${QUARKUS_REDIS_PASSWORD:-$REDIS_PASSWORD}"
export RABBITMQ_USER="${RABBITMQ_USER:-${SCRIM_RABBITMQ_USER:-guest}}"
export RABBITMQ_PASSWORD="${RABBITMQ_PASSWORD:-${SCRIM_RABBITMQ_PASSWORD:-guest}}"
export RABBITMQ_ERLANG_COOKIE="${RABBITMQ_ERLANG_COOKIE:-${SCRIM_RABBITMQ_ERLANG_COOKIE:-erlangcookie}}"

for service in $services; do
  echo "running $profile for $service"
  (cd "$service" && ./mvnw -B -q "-P${profile}" test)
done
