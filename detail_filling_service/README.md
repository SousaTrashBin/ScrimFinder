# Detail Filling Service

Java 21 / Quarkus service that enriches ScrimFinder data with Riot API match and player information. In cloud deploys this service is packaged once and deployed as three Google Cloud Functions:

- `getFilledMatch`
- `getRawMatchData`
- `getFilledPlayer`

The deploy scripts inject `RIOT_API_KEY` through Google Secret Manager.

## Local Development

```bash
./mvnw quarkus:dev
```

Redis-backed tests can use:

```bash
./redisInit.sh
./mvnw test
./redisShutdown.sh
```

## Build

```bash
./mvnw clean package
```

The service-root `Dockerfile` is the canonical container build file. Quarkus-generated Dockerfile variants are intentionally not tracked.

## Manual Requests

HTTP request collections live in `../tests/http/detail-filling.http`.
