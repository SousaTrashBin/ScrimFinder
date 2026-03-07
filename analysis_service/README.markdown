# LoL Analysis API — Rodrigo Neto (fc59850)

Decoupled microservices for **Draft Analysis**, **Build Analysis**, and **Player Performance Analysis**,
with a dedicated **Training Service** and a shared **Model Registry** (SQLite).

---

## Architecture

```
[EUW Match Data ~78GB]
        |
[Training Service :8000]  <-- POST /train/trigger  |  weekly cron
        | writes .pkl + metadata
[Model Registry DB]  (SQLite at /models/registry.db)
        | each service polls every 10 min (hot-reload, no restart needed)
   -----+----------+------------------+
[Draft :8001] [Build :8002] [Performance :8003]
```

**Key design properties:**
- One Docker volume (`model_store`) is shared across all containers — models land on disk once, all services read them.
- Analysis services hot-reload a new model within `MODEL_RELOAD_INTERVAL` seconds of being activated, without restarting.
- The registry uses a simple `is_active` flag + versioned rows so you can roll back by calling `POST /registry/models/{id}/activate`.

---

## Quickstart

```bash
# Build and start all 4 services
docker compose up --build

# Trigger training for all models
curl -X POST "http://localhost:8000/train/trigger?concern=all"

# Poll job status
curl http://localhost:8000/train/jobs/<job_id>

# Use the analysis APIs (once a model is active)
curl -X POST http://localhost:8001/analysis/draft -H "Content-Type: application/json" -d @examples/draft_request.json
curl -X POST http://localhost:8002/analysis/build -H "Content-Type: application/json" -d @examples/build_request.json
curl http://localhost:8003/analysis/performance/my-summoner-id
```

---

## Service URLs

| Service     | Port | Swagger UI                     |
|-------------|------|--------------------------------|
| Training    | 8000 | http://localhost:8000/docs     |
| Draft       | 8001 | http://localhost:8001/docs     |
| Build       | 8002 | http://localhost:8002/docs     |
| Performance | 8003 | http://localhost:8003/docs     |

---

## Generate OpenAPI YAML files

```bash
pip install -r requirements.txt
python export_openapi.py
# writes: openapi_training.yaml, openapi_draft.yaml,
#         openapi_build.yaml, openapi_performance.yaml
```

Paste any file into https://editor.swagger.io to preview.

---

## Weekly retraining (cron)

```cron
0 3 * * 1  curl -s -X POST "http://localhost:8000/train/trigger?concern=all"
```

Or trigger manually anytime:
```bash
curl -X POST "http://localhost:8000/train/trigger?concern=draft"
```

---

## File structure

```
rodrigo_api/
├── model_registry/
│   ├── db.py              # SQLite schema + CRUD
│   └── client.py          # RegistryClient — hot-reload model loader
├── training/
│   ├── main.py            # Training Service (:8000)
│   ├── runner.py          # Async job management
│   ├── train_draft.py
│   ├── train_build.py
│   └── train_performance.py
├── services/
│   ├── draft/main.py      # Draft Service (:8001)
│   ├── build/main.py      # Build Service (:8002)
│   └── performance/main.py# Performance Service (:8003)
├── shared/schemas.py      # Role, ErrorResponse, enums
├── models/                # .pkl files + registry.db (Docker volume)
├── Dockerfile
├── docker-compose.yml
├── export_openapi.py
└── requirements.txt
```

---

## Environment variables

| Variable                | Default                 | Description                                  |
|-------------------------|-------------------------|----------------------------------------------|
| `MODELS_DIR`            | `./models`              | Directory where .pkl files are saved         |
| `MODEL_REGISTRY_DB`     | `./models/registry.db`  | Path to the SQLite registry DB               |
| `MODEL_RELOAD_INTERVAL` | `600`                   | Seconds between registry polls per service   |