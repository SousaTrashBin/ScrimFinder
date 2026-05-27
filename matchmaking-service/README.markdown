# Matchmaking Service

Java 21 / Quarkus service for players, queues, matchmaking tickets, lobbies, and match lifecycle state. It coordinates with the ranking service for player MMR and publishes match results into the rest of the platform.

## Local Development

```bash
./mvnw quarkus:dev
```

## Tests

```bash
./mvnw test
```

System tests target a deployed Traefik endpoint:

```bash
BASE_URL=http://<traefik-address> ./mvnw -Psystem-tests -DsystemTests=true test
```

## Build

```bash
./mvnw clean package
```

The service-root `Dockerfile` is the canonical container build file.
