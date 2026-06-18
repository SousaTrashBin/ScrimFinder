#!/bin/bash

# Define colors for Git Bash
YELLOW='\033[1;33m'
CYAN='\033[1;36m'
GREEN='\033[1;32m'
NC='\033[0m' # No Color

echo -e "${YELLOW}🧹 Stopping and cleaning EVERYTHING...${NC}"
# Tear down standard/test setups and wipe their volumes
docker compose down -v --remove-orphans
docker compose -f docker-compose.test.yml down -v --remove-orphans
docker compose -f docker-compose.local.yml down -v --remove-orphans

# Nuclear option for anything left lingering
docker system prune -a -f --volumes
docker builder prune -f

echo -e "${CYAN}🔨 Rebuilding Local environment from scratch (no cache)...${NC}"
docker compose -f docker-compose.local.yml build --no-cache

echo -e "${GREEN}🚀 Starting Local services...${NC}"
docker compose -f docker-compose.local.yml up --force-recreate -d