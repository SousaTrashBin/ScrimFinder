#!/bin/bash
docker run -d --name redisDev -p 6379:6379 redis:8.6 --port 6379
docker exec -it redisDev redis-cli -p 6379 &>/dev/null &
docker run -d --name redisTest -p 6380:6380 redis:8.6 --port 6380
docker exec -it redisTest redis-cli -p 6380 &>/dev/null &