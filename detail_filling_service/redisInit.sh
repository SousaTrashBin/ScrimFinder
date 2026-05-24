#!/bin/bash
docker run -d --name redisDev -p 6379:6379 redis:8.6 --port 6379
docker exec -i redisDev redis-cli -p 6379 &
docker run -d --name redisTest -p 6380:6380 redis:8.6 --port 6380
docker exec -i redisTest redis-cli -p 6380 &