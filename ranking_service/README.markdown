# Ranking Service

Java 21 / Quarkus service for player accounts, Riot account links, queues, player rankings, leaderboards, and match-result MMR updates.

## Local Development

```bash
./mvnw quarkus:dev
```

## Tests

```bash
./mvnw test
```

The CI wrapper can run this service through the shared Java test path:

```bash
SERVICES=ranking_service ../scripts/ci/run-java-service-tests.sh fast-tests
```

## Build

```bash
./mvnw clean package
```

The service-root `Dockerfile` is the canonical container build file.
