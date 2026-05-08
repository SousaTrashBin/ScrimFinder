# ScrimFinder: League of Legends Cloud-Native Platform

ScrimFinder is a distributed, cloud-native platform designed for League of Legends matchmaking and advanced match
analytics. The system is built using a microservices architecture, orchestrated by Kubernetes, and designed to scale
independently based on demand.

## System Architecture

The platform consists of six core microservices categorized into three main domains:

### 1. Matchmaking & Ranking (Java / Quarkus)

* **Matchmaking Service:** Manages real-time player queues (Standard and Role-based) and lobby formation.
* **Ranking Service:** Handles leaderboards, user Elo rankings, and match results.

### 2. History & Data Filling (Java / Quarkus)

* **Match History Service:** The central repository for past custom match statistics.
* **Detail Filling Service:** Integrates with the Riot API to automatically fetch and cache match details.

### 3. Analytics & Insights (Python / FastAPI)

* **Training Service:** Handles large-scale data ingestion (~78GB dataset), feature extraction, and ML model training.
* **Analysis Service:** Provides real-time predictions for drafts, builds, and player performance using trained models.

---

## Technologies

* **Backend:** Java 21 (Quarkus), Python 3.11 (FastAPI).
* **Infrastructure:** Kubernetes (K8s), Docker Compose, Traefik (API Gateway).
* **Databases:** PostgreSQL (Core Data), Redis (Caching), SQLite (ML Metadata & Local Dataset).
* **Communication:** REST (Inter-service & External), gRPC (High-performance metadata exchange).
* **Tooling:** `uv` (Python environment management), `Maven` (Java build tool), `Ruff` (Python linting/formatting).

---

## API Documentation

All services provide interactive Swagger UI documentation at a standardized endpoint:
`http://localhost/api/v1/{service-name}/q/docs`

* **Matchmaking:** `/api/v1/matchmaking/q/docs`
* **Ranking:** `/api/v1/ranking/q/docs`
* **History:** `/api/v1/history/q/docs`
* **Riot Data:** `/api/v1/riot/q/docs`
* **Training:** `/api/v1/training/q/docs`
* **Analysis:** `/api/v1/analysis/q/docs`

---

## Getting Started

### Prerequisites

* [Docker](https://www.docker.com/) & Docker Compose

### Local Development

To format and test all services locally:

```bash
# format java (spotless) and python (ruff)
./scripts/format.sh

# run all unit and integration tests
./scripts/test.sh
```

### Deployment

### Prerequisites

* [Google Cloud SDK](https://cloud.google.com/sdk) (gcloud)
* [Docker](https://www.docker.com/) & [Helm](https://helm.sh/)
* **Environment Variables:**
  ```bash
  export SCRIM_PROJECT_ID="your-gcp-id"
  export SCRIM_REGION="europe-west1"
  export SCRIM_REPO_NAME="scrim-repo"
  export SCRIM_CLUSTER_NAME="scrim-cluster"
  export RIOT_API_KEY="RGAPI-..."
  export SCRIM_DB_USER="scrim_admin"
  export SCRIM_DB_PASSWORD="secure_password"
  ```

### Cloud Deployment (GKE)

The system is fully automated for Google Kubernetes Engine (GKE). The `boot.sh` script handles cluster creation,
Artifact Registry setup, image builds, and Helm deployment.

```bash
chmod +x boot.sh scripts/*.sh
```

### Deployment Scripts

| Script            | Purpose                                                                                                   | Usage                          |
|:------------------|:----------------------------------------------------------------------------------------------------------|:-------------------------------|
| **`deploy.sh`**   | Orchestrates the full GKE deployment, including cluster creation, image building, and Helm installation.  | `./scripts/deploy.sh`          |
| **`shutdown.sh`** | Tears down the entire GKE deployment, uninstalls Helm releases, and deletes the GKE cluster.              | `./scripts/shutdown.sh`        |
| **`verify.sh`**   | A wrapper that runs local tests, triggers a deployment, and then verifies all external service endpoints. | `./scripts/verify.sh`          |
| **`utils.sh`**    | A utils script for common DevOps tasks (logs, proxies, monitoring).                                       | `./scripts/utils.sh <command>` |

---

**What the deployment does:**

1. **GKE Provisioning:** Creates a Zonal Spot cluster for cost-efficiency.
2. **Registry Setup:** Configures Google Artifact Registry.
3. **Parallel Builds:** Builds and pushes all 6 microservices concurrently.
4. **Traefik Setup:** Installs official Traefik CRDs (IngressRoute, Middleware).
5. **Helm Install:** Deploys the full stack into the `scrimfinder` namespace.
6. **Smoke Tests:** Automatically verifies internal service health.

### Running the Deployment

````
./scripts/deploy.sh
````

### Verifying the Deployment

After `boot.sh` finishes, you can manually re-run the smoke tests or check service status:

```bash
# run automated internal health checks
helm test scrimfinder --namespace scrimfinder

# get the Traefik External IP for API access
kubectl get svc scrimfinder-traefik -n scrimfinder
```

### Stopping the System

To delete the GKE cluster and all associated resources:

```bash
./scripts/shutdown.sh
```

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
