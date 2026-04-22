# ScrimFinder: League of Legends Cloud-Native Platform

ScrimFinder is a distributed, cloud-native platform designed for League of Legends matchmaking and advanced match analytics. The system is built using a microservices architecture, orchestrated by Kubernetes, and designed to scale independently based on demand.

## System Architecture

The platform consists of six core microservices categorized into three main domains:

### 1. Matchmaking & Ranking (Java / Quarkus)
*   **Matchmaking Service:** Manages real-time player queues (Standard and Role-based) and lobby formation.
*   **Ranking Service:** Handles leaderboards, user Elo rankings, and match results.

### 2. History & Data Filling (Java / Quarkus)
*   **Match History Service:** The central repository for past custom match statistics.
*   **Detail Filling Service:** Integrates with the Riot API to automatically fetch and cache match details.

### 3. Analytics & Insights (Python / FastAPI)
*   **Training Service:** Handles large-scale data ingestion (~78GB dataset), feature extraction, and ML model training.
*   **Analysis Service:** Provides real-time predictions for drafts, builds, and player performance using trained models.

---

## Technologies

*   **Backend:** Java 21 (Quarkus), Python 3.11 (FastAPI).
*   **Infrastructure:** Kubernetes (K8s), Docker Compose, Traefik (API Gateway).
*   **Databases:** PostgreSQL (Core Data), Redis (Caching), SQLite (ML Metadata & Local Dataset).
*   **Communication:** REST (Inter-service & External), gRPC (High-performance metadata exchange).
*   **Tooling:** `uv` (Python environment management), `Maven` (Java build tool), `Ruff` (Python linting/formatting).

---

## API Documentation

All services provide interactive Swagger UI documentation at a standardized endpoint:
`http://localhost/api/v1/{service-name}/q/docs`

*   **Matchmaking:** `/api/v1/matchmaking/q/docs`
*   **Ranking:** `/api/v1/ranking/q/docs`
*   **History:** `/api/v1/history/q/docs`
*   **Riot Data:** `/api/v1/riot/q/docs`
*   **Training:** `/api/v1/training/q/docs`
*   **Analysis:** `/api/v1/analysis/q/docs`

---

## Getting Started

### Prerequisites
*   [Docker](https://www.docker.com/) & Docker Compose

### Running the System
The system is fully containerized. The boot script handles building all microservices (multi-stage Docker builds) and starting the infrastructure.

```bash
chmod +x deploy.sh shutdown.sh format.sh test.sh
./deploy.sh
```

### Local Development
To format and test all services locally:

```bash
# format java (spotless) and python (ruff)
./format.sh

# run all unit and integration tests
./test.sh
```


### Stopping the System
To shut down all services and remove volumes (including databases):

```bash
./shutdown.sh
```

---

## Project Structure

```text
├── analysis_service/       # Python FastAPI (ML Inference)
├── training_service/       # Python FastAPI (ML Training)
├── matchmaking_service/    # Java Quarkus (Queue Logic)
├── ranking_service/        # Java Quarkus (Elo & Leaderboards)
├── match_history_service/  # Java Quarkus (Persistence & Filters)
├── detail_filling_service/ # Java Quarkus (Riot API & Redis)
├── k8s/                    # Kubernetes Manifests (Traefik, Apps, Infra)
```
