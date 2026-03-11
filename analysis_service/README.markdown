# ScrimFinder Analysis API — v2.0

Unified ML platform for League of Legends match analysis.  
**Student:** Rodrigo Neto (fc59850)

---

## Architecture

```
analysis_service/
├── analysis_api/
│   ├── main.py                   ← Single FastAPI app, port 8000
│   ├── routers/
│   │   ├── games.py              ← /games
│   │   ├── features.py           ← /features
│   │   ├── datasets.py           ← /datasets
│   │   ├── training.py           ← /training/jobs
│   │   ├── models.py             ← /models
│   │   └── analysis.py          ← /analysis
│   ├── core/
│   │   ├── config.py             ← All env vars
│   │   ├── db.py                 ← SQLite schema + CRUD (platform.db)
│   │   └── schemas.py            ← All Pydantic models
│   ├── registry/
│   │   └── client.py             ← Model loader with hot-reload
│   ├── training/
│   │   ├── train_draft.py        ← Draft model trainer
│   │   ├── train_build.py        ← Build model trainer
│   │   └── train_performance.py  ← Performance model trainer
│   ├── ingestion/                ← TODO: feature_extractor.py
│   ├── datasets/                 ← TODO: builder.py
│   └── services/
│       └── champion/
│           └── queries.py        ← Champion SQL queries (EUW DB)
├── tests/
│   ├── conftest.py
│   └── test_api.py               ← 80 pytest tests
├── docker-compose.yml
├── Dockerfile
├── requirements.txt
└── pytest.ini
```

---

## Running

```powershell
# From analysis_service\
docker compose up --build

# API docs (browser)
# http://localhost:8000/docs        ← Swagger UI (interactive)
# http://localhost:8000/redoc       ← ReDoc (read-only)
# http://localhost:8000/openapi.json
```

---

## Running Tests

Tests run inside the Docker container, not on the host machine.

```powershell
# Run all fast tests (recommended)
docker exec scrimfinder_api pytest tests/test_api.py -v -m "not slow"

# Run everything including slow training tests (~10 min)
docker exec scrimfinder_api pytest tests/test_api.py -v

# Run a specific test class
docker exec scrimfinder_api pytest tests/test_api.py -v -k "games"
docker exec scrimfinder_api pytest tests/test_api.py -v -k "analysis"

# Run a single test
docker exec scrimfinder_api pytest tests/test_api.py::TestGames::test_ingest -v
```

---

## Getting the OpenAPI YAML

```powershell
# From anywhere on the host (container must be running)
curl.exe http://localhost:8000/openapi.json -o openapi.json
pip install pyyaml
python -c "import json,yaml; open('openapi.yaml','w').write(yaml.dump(json.load(open('openapi.json')),sort_keys=False,allow_unicode=True))"
```

---

## Endpoint Reference

### Pipeline order
```
POST /games                      →  1. Ingest raw match JSON
POST /features/extract           →  2. Extract feature vectors
POST /datasets/build             →  3. Curate a training dataset
POST /training/jobs              →  4. Train a model on that dataset
POST /training/jobs/{id}/deploy  →  5. Activate the model
POST /analysis/*                 →  6. Serve predictions
```

### Games  `/games`
| Method | Path | Description |
|--------|------|-------------|
| POST | `/games` | Ingest one match JSON |
| POST | `/games/batch` | Ingest up to 1000 matches |
| GET  | `/games` | List games (paginated) |
| GET  | `/games/{id}` | Fetch full game + raw JSON |
| DELETE | `/games/{id}` | Remove game and its features |

### Features  `/features`
| Method | Path | Description |
|--------|------|-------------|
| POST | `/features/extract` | Extract feature vectors (from stored game or inline JSON) |
| GET  | `/features/{game_id}` | Retrieve cached feature vectors |
| DELETE | `/features/{game_id}` | Delete cached features |

> **Status:** Returns correct schema with empty vectors until `ingestion/feature_extractor.py` is implemented.

### Datasets  `/datasets`
| Method | Path | Description |
|--------|------|-------------|
| POST | `/datasets` | Register dataset metadata only |
| POST | `/datasets/build` | Register + build in background |
| POST | `/datasets/{id}/build` | Rebuild existing dataset |
| GET  | `/datasets` | List all datasets |
| GET  | `/datasets/{id}` | Get dataset metadata |
| DELETE | `/datasets/{id}` | Delete dataset (guards active jobs) |

> **Status:** Builder marks dataset `ready` immediately with `row_count=0` until `datasets/builder.py` is implemented.

### Training  `/training/jobs`
| Method | Path | Description |
|--------|------|-------------|
| POST | `/training/jobs` | Create and start a training job |
| GET  | `/training/jobs` | List all jobs |
| GET  | `/training/jobs/{id}` | Get job status + progress |
| POST | `/training/jobs/{id}/cancel` | Cancel running job |
| POST | `/training/jobs/{id}/deploy` | Activate produced model |

**Creating a job:**
```json
{
  "concern": "draft",
  "algorithm": "auto",
  "dataset_id": "ds_abc123",
  "sample": 0.05
}
```

`dataset_id` is preferred (fast, reproducible). If omitted, the job loads directly from the EUW SQLite DB using `sample`/`limit`/`match_type` filters.

**Algorithm options:** `auto` | `logistic` | `gbm` | `random_forest` | `lightgbm`

### Models  `/models`
| Method | Path | Description |
|--------|------|-------------|
| GET  | `/models` | List all models |
| GET  | `/models/active` | List active (deployed) models |
| GET  | `/models/{id}` | Metadata: accuracy, F1, date, hyperparams |
| POST | `/models/{id}/activate` | Deploy model |
| POST | `/models/{id}/deactivate` | Take model offline |
| DELETE | `/models/{id}` | Delete record (must deactivate first) |

### Analysis  `/analysis`
| Method | Path | Description |
|--------|------|-------------|
| POST | `/analysis/draft` | Win probability for a 5v5 draft |
| POST | `/analysis/build` | Build effectiveness score |
| POST | `/analysis/player` | Player performance + tips |
| POST | `/analysis/game` | Post-game breakdown for all 10 players |
| POST | `/analysis/champion` | Champion stats from EUW DB |

---

## Database Schema  (`platform.db`)

```sql
games           -- raw match JSON blobs
features        -- extracted feature vectors (per game × concern)
datasets        -- dataset definitions + filter rules
dataset_games   -- dataset ↔ game membership
models          -- model registry (algorithm, metrics, file_path)
training_jobs   -- persistent job records (survive restarts)
```

---

## What's Fully Implemented vs Stubbed

| Component | Status | Notes |
|-----------|--------|-------|
| `core/db.py` | ✅ Full | All CRUD, WAL mode, FK constraints |
| `core/schemas.py` | ✅ Full | All request/response types |
| `routers/games.py` | ✅ Full | Ingest, list, fetch, delete |
| `routers/features.py` | ⚠️ Schema only | Returns empty vectors |
| `routers/datasets.py` | ⚠️ Schema only | Builder marks ready immediately |
| `routers/training.py` | ✅ Full | Job lifecycle, DB-persisted |
| `routers/models.py` | ✅ Full | List, activate, deactivate, delete |
| `routers/analysis.py` | ⚠️ Partial | Draft/build/champion use real logic; player/game are stubs |
| `registry/client.py` | ✅ Full | Hot-reload watcher |
| `ingestion/feature_extractor.py` | ❌ TODO | Define feature schema first |
| `datasets/builder.py` | ❌ TODO | Needs feature_extractor first |
| `training/trainer.py` | ❌ TODO | Unified trainer dispatcher |
| `training/algorithms.py` | ❌ TODO | LightGBM, LogisticRegression options |

---

## What to Build Next (in order)

### 1. `ingestion/feature_extractor.py`
Takes a raw match JSON and produces feature vectors for each concern:

```python
def extract(raw: dict, concerns: list[str]) -> dict[str, tuple[list, list]]:
    """
    Returns {concern: (feature_vector, feature_names)}

    Draft features:   10 champion IDs as multi-hot (blue team + red team)
    Build features:   champion_id + position + items (multi-hot) + runes (multi-hot)
    Performance:      kills, deaths, assists, gold, cs, dmg_champs, vision, kda, kp, duration
    """
```

### 2. `datasets/builder.py`
Once features exist, materialise them into `.npz` files for fast training:

```python
def build(dataset_id: str, concern: str, filters: dict) -> tuple[str, int, int]:
    """Query features table → .npz file. Returns (file_path, game_count, row_count)."""
```

### 3. `training/trainer.py` + `training/algorithms.py`
Replace the `train_*.py` shims with a proper dispatcher that loads `.npz` directly instead of querying SQLite during training.

### 4. Wire up `/analysis/player` and `/analysis/game`
Both return stubs. They need `query_player_stats(summoner_id, last_n)` against LEAGUE_DB and a loaded performance model via `RegistryClient`.

---

## Tests

```
tests/test_api.py  —  80 tests, 8 test classes

TestSystem        — root, health, OpenAPI schema, docs
TestGames         — ingest, batch, fetch, list, delete, pagination
TestFeatures      — extract, retrieve, filter, delete
TestDatasets      — create, build, list, get, delete, guard
TestTrainingJobs  — create, list, get, cancel, deploy, schema
TestModels        — list, active, get, activate, deactivate, delete
TestAnalysis      — draft, build, player, game, champion
TestIntegration   — full pipeline flows end-to-end

Markers:
  @pytest.mark.slow        training tests (minutes) — excluded by -m "not slow"
  @pytest.mark.requires_db needs LEAGUE_DB configured
```

Current status: **79/79 fast tests passing.**