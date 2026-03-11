# ScrimFinder Analysis API — v2.0

Unified ML platform for League of Legends match analysis.  
**Student:** Rodrigo Neto (fc59850)

---

## Architecture

```
analysis_service/
├── api/
│   ├── main.py                   ← Single FastAPI app, port 8000
│   └── routers/
│       ├── games.py              ← /games
│       ├── features.py           ← /features
│       ├── datasets.py           ← /datasets
│       ├── training.py           ← /training/jobs
│       ├── models.py             ← /models
│       └── analysis.py          ← /analysis
├── core/
│   ├── config.py                 ← All env vars
│   ├── db.py                     ← SQLite schema + CRUD (platform.db)
│   └── schemas.py                ← All Pydantic models
├── registry/
│   └── client.py                 ← Model loader with hot-reload
├── training/
│   └── train_draft.py            ← Draft model trainer (shim)
├── ingestion/                    ← TODO: feature_extractor.py
├── datasets/                     ← TODO: builder.py
├── tests/
│   ├── conftest.py
│   └── test_api.py               ← 60+ pytest tests
├── docker-compose.yml
├── requirements.txt
└── pytest.ini
```

---

## Running

```powershell
# Start
cd analysis_service
docker compose up --build

# API docs
# http://localhost:8000/docs      ← Swagger UI
# http://localhost:8000/redoc     ← ReDoc
# http://localhost:8000/openapi.json

# Run tests (from analysis_service/)
pytest tests/test_api.py -v              # all tests except slow
pytest tests/test_api.py -v -m "not slow"   # skip training tests
pytest tests/test_api.py -v -k "games"  # only game tests
```

---

## Endpoint Reference

### Pipeline order
```
POST /games              →  1. Ingest raw match JSON
POST /features/extract   →  2. Extract feature vectors
POST /datasets/build     →  3. Curate a training dataset
POST /training/jobs      →  4. Train a model on that dataset
POST /training/jobs/{id}/deploy  → 5. Activate the model
POST /analysis/*         →  6. Serve predictions
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

> **Status:** Extraction returns correct schema with empty vectors.  
> Implement `ingestion/feature_extractor.py` to fill this in.

### Datasets  `/datasets`
| Method | Path | Description |
|--------|------|-------------|
| POST | `/datasets` | Register dataset metadata only |
| POST | `/datasets/build` | Register + build in background |
| POST | `/datasets/{id}/build` | Rebuild existing dataset |
| GET  | `/datasets` | List all datasets |
| GET  | `/datasets/{id}` | Get dataset metadata |
| DELETE | `/datasets/{id}` | Delete dataset (guards active jobs) |

> **Status:** Builder marks dataset `ready` immediately with `row_count=0`.  
> Implement `datasets/builder.py` to materialise real `.npz` files.

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
  "dataset_id": "ds_abc123",      ← preferred: use a built dataset
  "sample": 0.05                  ← fallback: inline filter (5% of EUW DB)
}
```

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
| `api/routers/games.py` | ✅ Full | Ingest, list, fetch, delete |
| `api/routers/features.py` | ⚠️ Schema only | Returns empty vectors |
| `api/routers/datasets.py` | ⚠️ Schema only | Builder marks ready immediately |
| `api/routers/training.py` | ✅ Full | Job lifecycle, DB-persisted |
| `api/routers/models.py` | ✅ Full | List, activate, deactivate, delete |
| `api/routers/analysis.py` | ⚠️ Partial | Draft/build/champion hit real logic; player/game are stubs |
| `registry/client.py` | ✅ Full | Hot-reload watcher |
| `ingestion/feature_extractor.py` | ❌ TODO | Define feature schema first |
| `datasets/builder.py` | ❌ TODO | Needs feature_extractor first |
| `training/trainer.py` | ❌ TODO | Dispatch to algorithm-specific trainers |
| `training/algorithms.py` | ❌ TODO | LightGBM, LogisticRegression options |

---

## What to Build Next (in order)

### 1. `ingestion/feature_extractor.py`
This is the most important missing piece. It takes a raw match JSON and produces feature vectors for each concern:

```python
def extract(raw: dict, concerns: list[str]) -> dict[str, tuple[list, list]]:
    """
    Returns {concern: (feature_vector, feature_names)}
    
    Draft features:   [champ_id_blue_0..4, champ_id_red_0..4]  (10 ints → multi-hot encoded)
    Build features:   [champion_id, position_id, item_0..5 multi-hot, rune_0..3 multi-hot]
    Performance:      [kills, deaths, assists, gold, cs, dmg_champs, vision, kda, kp, duration]
    """
```

The key question: **what format is your raw game JSON?** Riot API format vs your internal DB format will change the field names. Check what `SAMPLE_GAME` looks like from your Riot integration.

### 2. `datasets/builder.py`
Once features exist:
```python
def build(dataset_id: str, concern: str, filters: dict) -> tuple[str, int, int]:
    """Query features table with filters, materialise to .npz, return (path, game_count, row_count)"""
```

### 3. `training/trainer.py` + `training/algorithms.py`
Replace the legacy `train_draft.py` shim with a proper dispatcher that:
- Loads dataset from `.npz` (fast — no SQL during training)
- Picks algorithm based on concern + user choice
- Tracks hyperparams in the DB

### 4. Fix `/analysis/player` and `/analysis/game`
These return stubs. They need:
- `query_player_stats(summoner_id, last_n)` — SQL against LEAGUE_DB
- Performance model loaded via `RegistryClient`

---

## Tests

```
tests/test_api.py  —  60+ tests, 7 test classes

TestSystem        — root, health, OpenAPI schema, docs
TestGames         — ingest, batch, fetch, list, delete, pagination
TestFeatures      — extract, retrieve, filter, delete
TestDatasets      — create, build, list, get, delete, guard
TestTrainingJobs  — create, list, get, cancel, deploy, schema
TestModels        — list, active, get, activate, deactivate, delete
TestAnalysis      — draft, build, player, game, champion
TestIntegration   — full pipeline flows end-to-end

Markers:
  @pytest.mark.slow          training tests (minutes) — excluded by default
  @pytest.mark.requires_db   needs LEAGUE_DB
```

Run fast tests only:
```bash
pytest -m "not slow"          # ~5 seconds
pytest -m "not slow and not requires_db"   # no DB needed at all
```