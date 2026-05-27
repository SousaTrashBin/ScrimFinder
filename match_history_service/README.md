# Match History Service

Java 21 / Quarkus service responsible for persisted match history, match filtering/sorting, player and team statistics, and forwarding saved matches to the training service through gRPC.

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
SERVICES=match_history_service ../scripts/ci/run-java-service-tests.sh fast-tests
```

## Build

```bash
./mvnw clean package
```

The service-root `Dockerfile` is the canonical container build file.

## Manual Requests

HTTP request collections live in `../tests/http/match-history.http`.
