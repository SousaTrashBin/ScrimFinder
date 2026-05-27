# ScrimFinder

ScrimFinder is a cloud-native League of Legends platform for custom matchmaking, rankings, match history, Riot data filling, and ML-assisted analysis. The production path runs on Google Cloud with Java Quarkus services in GKE, Python FastAPI services, and the Riot detail filling API deployed as Google Cloud Functions.

## Services

| Service | Runtime | Responsibility |
|:--|:--|:--|
| `matchmaking-service` | Java 21 / Quarkus | Players, queues, tickets, lobbies, and match lifecycle. |
| `ranking_service` | Java 21 / Quarkus | Player rankings, queues, leaderboards, and match-result MMR updates. |
| `match_history_service` | Java 21 / Quarkus | Match storage, filtering, history queries, and training sync events. |
| `detail_filling_service` | Java 21 / Quarkus on Cloud Functions | Riot API match/player enrichment and raw match data endpoints. |
| `training_service` | Python 3.11 / FastAPI | Dataset ingestion, feature extraction, training metadata, and training gRPC server. |
| `analysis_service` | Python 3.11 / FastAPI | Champion, draft, build, and performance analysis endpoints. |

## Repository Layout

```text
analysis_service/          Python analysis API
detail_filling_service/    Riot data filling Cloud Functions source
docs/architecture/         Architecture diagrams
docs/phases/               Project phase notes
infrastructure/terraform/  GCP infrastructure
k8s/charts/scrimfinder/    Helm chart
match_history_service/     Java match history service
matchmaking-service/       Java matchmaking service
ranking_service/           Java ranking service
scripts/                   CI, deploy, test, and utility scripts
tests/http/                Manual HTTP request collections
training_service/          Python training API, gRPC server, datasets, and proto
```

## Local Checks

```bash
./scripts/format.sh
./scripts/test.sh
```

Python gRPC files are generated, not committed:

```bash
./scripts/python/generate-grpc.sh
```

## Deployment

Required environment:

```bash
export SCRIM_PROJECT_ID="your-gcp-project"
export SCRIM_REGION="europe-west1"
export SCRIM_REPO_NAME="scrimfinder"
export SCRIM_CLUSTER_NAME="scrimfinder"
export RIOT_API_KEY="RGAPI-..."
export SCRIM_DB_USER="scrim_admin"
export SCRIM_DB_PASSWORD="secure_password"
```

Deploy infrastructure, images, Cloud Functions, and the Helm application:

```bash
./scripts/deploy.sh
```

Tear down the stack:

```bash
./scripts/shutdown.sh
```

## API Docs

When deployed through Traefik, service docs are exposed under:

```text
/api/v1/matchmaking/q/docs
/api/v1/ranking/q/docs
/api/v1/history/q/docs
/api/v1/riot/q/docs
/api/v1/training/q/docs
/api/v1/analysis/q/docs
```

Manual request files live in `tests/http/`.
