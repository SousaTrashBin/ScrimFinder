# Run this from inside analysis_service\
# powershell -ExecutionPolicy Bypass -File setup_files.ps1

$base = "analysis_api"

# ── core/config.py ────────────────────────────────────────────
@'
import os
from pathlib import Path

_HERE = Path(__file__).resolve().parent.parent

class _Config:
    LEAGUE_DB:    str = os.environ.get("LEAGUE_DB",    str(_HERE.parent / "dataset" / "league_data.db"))
    PLATFORM_DB:  str = os.environ.get("PLATFORM_DB",  str(_HERE / "data" / "platform.db"))
    MODELS_DIR:   str = os.environ.get("MODELS_DIR",   str(_HERE / "data" / "models"))
    GAMES_DIR:    str = os.environ.get("GAMES_DIR",    str(_HERE / "data" / "games"))
    DATASETS_DIR: str = os.environ.get("DATASETS_DIR", str(_HERE / "data" / "datasets"))
    MODEL_RELOAD_INTERVAL: int = int(os.environ.get("MODEL_RELOAD_INTERVAL", 600))

    def ensure_dirs(self):
        for d in [self.MODELS_DIR, self.GAMES_DIR, self.DATASETS_DIR,
                  str(Path(self.PLATFORM_DB).parent)]:
            Path(d).mkdir(parents=True, exist_ok=True)

cfg = _Config()
'@ | Set-Content "$base\core\config.py" -Encoding UTF8

# ── core/db.py ────────────────────────────────────────────────
@'
import json, sqlite3
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional
from analysis_api.core.config import cfg

@contextmanager
def get_conn():
    conn = sqlite3.connect(cfg.PLATFORM_DB, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA foreign_keys=ON")
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()

def now_iso():
    return datetime.now(timezone.utc).isoformat()

_SCHEMA = """
CREATE TABLE IF NOT EXISTS games (
    id TEXT PRIMARY KEY, source TEXT NOT NULL DEFAULT 'manual',
    patch TEXT, match_type TEXT, duration_sec INTEGER, platform TEXT,
    raw_json TEXT NOT NULL, ingested_at TEXT NOT NULL);
CREATE INDEX IF NOT EXISTS idx_games_patch      ON games(patch);
CREATE INDEX IF NOT EXISTS idx_games_match_type ON games(match_type);
CREATE INDEX IF NOT EXISTS idx_games_source     ON games(source);

CREATE TABLE IF NOT EXISTS features (
    game_id TEXT NOT NULL, concern TEXT NOT NULL,
    feature_vector TEXT NOT NULL, feature_names TEXT NOT NULL,
    schema_version TEXT NOT NULL DEFAULT '1', extracted_at TEXT NOT NULL,
    PRIMARY KEY (game_id, concern),
    FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE);
CREATE INDEX IF NOT EXISTS idx_features_concern ON features(concern);

CREATE TABLE IF NOT EXISTS datasets (
    id TEXT PRIMARY KEY, name TEXT NOT NULL, description TEXT,
    concern TEXT NOT NULL, filters TEXT NOT NULL DEFAULT '{}',
    game_count INTEGER NOT NULL DEFAULT 0, row_count INTEGER NOT NULL DEFAULT 0,
    file_path TEXT, status TEXT NOT NULL DEFAULT 'pending',
    created_at TEXT NOT NULL, built_at TEXT);

CREATE TABLE IF NOT EXISTS dataset_games (
    dataset_id TEXT NOT NULL, game_id TEXT NOT NULL,
    PRIMARY KEY (dataset_id, game_id),
    FOREIGN KEY (dataset_id) REFERENCES datasets(id) ON DELETE CASCADE,
    FOREIGN KEY (game_id)    REFERENCES games(id)    ON DELETE CASCADE);

CREATE TABLE IF NOT EXISTS models (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    concern TEXT NOT NULL, algorithm TEXT NOT NULL DEFAULT 'gbm',
    dataset_id TEXT, version TEXT NOT NULL, file_path TEXT NOT NULL,
    metrics TEXT NOT NULL DEFAULT '{}', hyperparams TEXT NOT NULL DEFAULT '{}',
    feature_names TEXT, is_active INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL, activated_at TEXT,
    FOREIGN KEY (dataset_id) REFERENCES datasets(id));
CREATE INDEX IF NOT EXISTS idx_models_concern_active ON models(concern, is_active);

CREATE TABLE IF NOT EXISTS training_jobs (
    id TEXT PRIMARY KEY, concern TEXT NOT NULL, algorithm TEXT NOT NULL DEFAULT 'auto',
    dataset_id TEXT, status TEXT NOT NULL DEFAULT 'PENDING',
    progress INTEGER NOT NULL DEFAULT 0, stage TEXT NOT NULL DEFAULT 'Queued',
    filters TEXT NOT NULL DEFAULT '{}', metrics TEXT, model_id INTEGER, error TEXT,
    created_at TEXT NOT NULL, started_at TEXT, completed_at TEXT,
    FOREIGN KEY (dataset_id) REFERENCES datasets(id),
    FOREIGN KEY (model_id)   REFERENCES models(id));
CREATE INDEX IF NOT EXISTS idx_jobs_concern ON training_jobs(concern);
CREATE INDEX IF NOT EXISTS idx_jobs_status  ON training_jobs(status);
"""

def init_db():
    cfg.ensure_dirs()
    with get_conn() as conn:
        conn.executescript(_SCHEMA)

def count_games():
    with get_conn() as conn:
        return conn.execute("SELECT COUNT(*) FROM games").fetchone()[0]

def insert_game(game_id, raw, source="manual"):
    patch        = raw.get("patch") or raw.get("gameVersion")
    match_type   = raw.get("match_type") or raw.get("gameType") or raw.get("queueType")
    duration_sec = raw.get("duration_sec") or raw.get("gameDuration")
    platform     = raw.get("platform") or raw.get("platformId")
    with get_conn() as conn:
        conn.execute(
            "INSERT OR REPLACE INTO games (id,source,patch,match_type,duration_sec,platform,raw_json,ingested_at) VALUES (?,?,?,?,?,?,?,?)",
            (game_id, source, patch, match_type, duration_sec, platform, json.dumps(raw), now_iso()))

def get_game(game_id):
    with get_conn() as conn:
        row = conn.execute("SELECT * FROM games WHERE id=?", (game_id,)).fetchone()
    if row is None:
        return None
    d = dict(row)
    d["raw_json"] = json.loads(d["raw_json"])
    return d

def list_games(source=None, patch=None, match_type=None, limit=50, offset=0):
    clauses, params = [], []
    if source:     clauses.append("source=?");     params.append(source)
    if patch:      clauses.append("patch=?");      params.append(patch)
    if match_type: clauses.append("match_type=?"); params.append(match_type)
    where = ("WHERE " + " AND ".join(clauses)) if clauses else ""
    with get_conn() as conn:
        total = conn.execute(f"SELECT COUNT(*) FROM games {where}", params).fetchone()[0]
        rows  = conn.execute(
            f"SELECT id,source,patch,match_type,duration_sec,ingested_at FROM games {where} ORDER BY ingested_at DESC LIMIT ? OFFSET ?",
            params + [limit, offset]).fetchall()
    return [dict(r) for r in rows], total

def upsert_features(game_id, concern, vector, names, schema_version="1"):
    with get_conn() as conn:
        conn.execute(
            "INSERT OR REPLACE INTO features (game_id,concern,feature_vector,feature_names,schema_version,extracted_at) VALUES (?,?,?,?,?,?)",
            (game_id, concern, json.dumps(vector), json.dumps(names), schema_version, now_iso()))

def get_features(game_id, concern=None):
    with get_conn() as conn:
        if concern:
            rows = conn.execute("SELECT * FROM features WHERE game_id=? AND concern=?", (game_id, concern)).fetchall()
        else:
            rows = conn.execute("SELECT * FROM features WHERE game_id=?", (game_id,)).fetchall()
    result = []
    for r in rows:
        d = dict(r)
        d["feature_vector"] = json.loads(d["feature_vector"])
        d["feature_names"]  = json.loads(d["feature_names"])
        result.append(d)
    return result

def insert_dataset(ds_id, name, concern, filters, description=""):
    with get_conn() as conn:
        conn.execute(
            "INSERT INTO datasets (id,name,description,concern,filters,status,created_at) VALUES (?,?,?,?,?,'pending',?)",
            (ds_id, name, description, concern, json.dumps(filters), now_iso()))

def update_dataset_status(ds_id, status, game_count=0, row_count=0, file_path=None):
    with get_conn() as conn:
        conn.execute(
            "UPDATE datasets SET status=?,game_count=?,row_count=?,file_path=?,built_at=? WHERE id=?",
            (status, game_count, row_count, file_path, now_iso(), ds_id))

def get_dataset(ds_id):
    with get_conn() as conn:
        row = conn.execute("SELECT * FROM datasets WHERE id=?", (ds_id,)).fetchone()
    if row is None:
        return None
    d = dict(row)
    d["filters"] = json.loads(d["filters"])
    return d

def list_datasets(concern=None):
    with get_conn() as conn:
        if concern:
            rows = conn.execute("SELECT * FROM datasets WHERE concern=? ORDER BY created_at DESC", (concern,)).fetchall()
        else:
            rows = conn.execute("SELECT * FROM datasets ORDER BY created_at DESC").fetchall()
    result = []
    for r in rows:
        d = dict(r)
        d["filters"] = json.loads(d["filters"])
        result.append(d)
    return result

def delete_dataset(ds_id):
    with get_conn() as conn:
        cur = conn.execute("DELETE FROM datasets WHERE id=?", (ds_id,))
    return cur.rowcount > 0

def register_model(concern, algorithm, version, file_path, metrics, hyperparams=None, dataset_id=None, feature_names=None):
    with get_conn() as conn:
        cur = conn.execute(
            "INSERT INTO models (concern,algorithm,dataset_id,version,file_path,metrics,hyperparams,feature_names,is_active,created_at) VALUES (?,?,?,?,?,?,?,?,0,?)",
            (concern, algorithm, dataset_id, version, file_path,
             json.dumps(metrics), json.dumps(hyperparams or {}), json.dumps(feature_names or []), now_iso()))
    return cur.lastrowid

def activate_model(model_id):
    with get_conn() as conn:
        row = conn.execute("SELECT concern FROM models WHERE id=?", (model_id,)).fetchone()
        if row is None:
            raise ValueError(f"No model id={model_id}")
        conn.execute("UPDATE models SET is_active=0 WHERE concern=? AND is_active=1", (row["concern"],))
        conn.execute("UPDATE models SET is_active=1,activated_at=? WHERE id=?", (now_iso(), model_id))

def deactivate_model(model_id):
    with get_conn() as conn:
        conn.execute("UPDATE models SET is_active=0 WHERE id=?", (model_id,))

def get_active_model(concern):
    with get_conn() as conn:
        row = conn.execute("SELECT * FROM models WHERE concern=? AND is_active=1", (concern,)).fetchone()
    if row is None:
        return None
    d = dict(row)
    for f in ("metrics", "hyperparams", "feature_names"):
        d[f] = json.loads(d[f]) if d[f] else {}
    d["is_active"] = bool(d["is_active"])
    return d

def get_model_by_id(model_id):
    with get_conn() as conn:
        row = conn.execute("SELECT * FROM models WHERE id=?", (model_id,)).fetchone()
    if row is None:
        return None
    d = dict(row)
    for f in ("metrics", "hyperparams", "feature_names"):
        d[f] = json.loads(d[f]) if d[f] else {}
    d["is_active"] = bool(d["is_active"])
    return d

def list_models(concern=None, active_only=False):
    clauses, params = [], []
    if concern:     clauses.append("concern=?");   params.append(concern)
    if active_only: clauses.append("is_active=1")
    where = ("WHERE " + " AND ".join(clauses)) if clauses else ""
    with get_conn() as conn:
        rows = conn.execute(f"SELECT * FROM models {where} ORDER BY created_at DESC", params).fetchall()
    result = []
    for r in rows:
        d = dict(r)
        for f in ("metrics", "hyperparams", "feature_names"):
            d[f] = json.loads(d[f]) if d[f] else {}
        d["is_active"] = bool(d["is_active"])
        result.append(d)
    return result

def create_job(job_id, concern, algorithm="auto", dataset_id=None, filters=None):
    with get_conn() as conn:
        conn.execute(
            "INSERT INTO training_jobs (id,concern,algorithm,dataset_id,filters,created_at) VALUES (?,?,?,?,?,?)",
            (job_id, concern, algorithm, dataset_id, json.dumps(filters or {}), now_iso()))

def update_job(job_id, **kwargs):
    sets, params = [], []
    for k, v in kwargs.items():
        sets.append(f"{k}=?")
        params.append(json.dumps(v) if isinstance(v, (dict, list)) else v)
    if not sets:
        return
    params.append(job_id)
    with get_conn() as conn:
        conn.execute(f"UPDATE training_jobs SET {', '.join(sets)} WHERE id=?", params)

def get_job(job_id):
    with get_conn() as conn:
        row = conn.execute("SELECT * FROM training_jobs WHERE id=?", (job_id,)).fetchone()
    if row is None:
        return None
    d = dict(row)
    for f in ("filters", "metrics"):
        d[f] = json.loads(d[f]) if d[f] else {}
    return d

def list_jobs(concern=None, status=None, limit=100):
    clauses, params = [], []
    if concern: clauses.append("concern=?"); params.append(concern)
    if status:  clauses.append("status=?");  params.append(status)
    where = ("WHERE " + " AND ".join(clauses)) if clauses else ""
    with get_conn() as conn:
        rows = conn.execute(
            f"SELECT * FROM training_jobs {where} ORDER BY created_at DESC LIMIT ?",
            params + [limit]).fetchall()
    result = []
    for r in rows:
        d = dict(r)
        for f in ("filters", "metrics"):
            d[f] = json.loads(d[f]) if d[f] else {}
        result.append(d)
    return result
'@ | Set-Content "$base\core\db.py" -Encoding UTF8

# ── core/schemas.py ───────────────────────────────────────────
@'
from __future__ import annotations
from enum import Enum
from typing import Optional
from pydantic import BaseModel, Field

class Concern(str, Enum):
    DRAFT="draft"; BUILD="build"; PERFORMANCE="performance"

class Algorithm(str, Enum):
    LOGISTIC="logistic"; GBM="gbm"; RANDOM_FOREST="random_forest"
    LIGHTGBM="lightgbm"; AUTO="auto"

class JobStatus(str, Enum):
    PENDING="PENDING"; RUNNING="RUNNING"; COMPLETED="COMPLETED"
    FAILED="FAILED"; CANCELLED="CANCELLED"

class Role(str, Enum):
    TOP="TOP"; JUNGLE="JUNGLE"; MID="MID"; BOT="BOT"; SUPPORT="SUPPORT"

class ImpactLevel(str, Enum):
    LOW="LOW"; MEDIUM="MEDIUM"; HIGH="HIGH"

class TipCategory(str, Enum):
    VISION="VISION"; FARMING="FARMING"; DAMAGE="DAMAGE"; POSITIONING="POSITIONING"
    OBJECTIVE_CONTROL="OBJECTIVE_CONTROL"; BUILDS="BUILDS"; GAME_SENSE="GAME_SENSE"

class ErrorResponse(BaseModel):
    code: int = Field(..., example=404)
    message: str = Field(..., example="Resource not found.")
    details: Optional[str] = None

class PaginatedMeta(BaseModel):
    total: int; limit: int; offset: int

# ── Games ─────────────────────────────────────────────────────
class GameIngest(BaseModel):
    id: Optional[str] = None
    data: dict = Field(..., description="Full match JSON")
    source: str = "manual"

class GameIngested(BaseModel):
    id: str; source: str; patch: Optional[str]; match_type: Optional[str]; ingested_at: str

class GameDetail(GameIngested):
    duration_sec: Optional[int]; platform: Optional[str]; raw_json: dict

class GameListResponse(BaseModel):
    games: list[GameIngested]; meta: PaginatedMeta

class BatchIngestRequest(BaseModel):
    games: list[GameIngest]; source: str = "manual"

class BatchIngestResponse(BaseModel):
    ingested: int; skipped: int; errors: list[dict]

# ── Features ──────────────────────────────────────────────────
class FeatureExtractRequest(BaseModel):
    game_id: Optional[str] = None
    raw_data: Optional[dict] = None
    concerns: list[Concern] = Field(default=[Concern.DRAFT, Concern.BUILD, Concern.PERFORMANCE])
    store: bool = True

class FeatureVector(BaseModel):
    game_id: str; concern: str; feature_vector: list[float]
    feature_names: list[str]; schema_version: str; extracted_at: str

class FeatureExtractResponse(BaseModel):
    game_id: str; features: list[FeatureVector]; stored: bool

# ── Datasets ──────────────────────────────────────────────────
class DatasetFilters(BaseModel):
    patch: Optional[str] = None
    patches: Optional[list[str]] = None
    match_type: Optional[str] = None
    min_duration_sec: Optional[int] = None
    max_duration_sec: Optional[int] = None
    source: Optional[str] = None
    date_from: Optional[str] = None
    date_to: Optional[str] = None

class DatasetCreateRequest(BaseModel):
    name: str = Field(..., min_length=1, max_length=128)
    concern: Concern; description: str = ""
    filters: DatasetFilters = Field(default_factory=DatasetFilters)

class DatasetBuildRequest(BaseModel):
    name: str; concern: Concern; description: str = ""
    filters: DatasetFilters = Field(default_factory=DatasetFilters)
    async_build: bool = True

class DatasetMeta(BaseModel):
    id: str; name: str; description: str; concern: str; filters: dict
    game_count: int; row_count: int; status: str; created_at: str
    built_at: Optional[str]; file_path: Optional[str]

class DatasetListResponse(BaseModel):
    datasets: list[DatasetMeta]

# ── Training ──────────────────────────────────────────────────
class TrainingJobCreate(BaseModel):
    concern: Concern; algorithm: Algorithm = Algorithm.AUTO
    dataset_id: Optional[str] = None
    sample: Optional[float] = Field(None, ge=0.01, le=1.0)
    limit: Optional[int] = Field(None, ge=1000)
    match_type: Optional[str] = None

class TrainingJobResponse(BaseModel):
    id: str; concern: str; algorithm: str; dataset_id: Optional[str]
    status: str; progress: int; stage: str; filters: dict
    metrics: Optional[dict]; model_id: Optional[int]; error: Optional[str]
    created_at: str; started_at: Optional[str]; completed_at: Optional[str]

class TrainingJobListResponse(BaseModel):
    jobs: list[TrainingJobResponse]

# ── Models ────────────────────────────────────────────────────
class ModelMeta(BaseModel):
    id: int; concern: str; algorithm: str; dataset_id: Optional[str]
    version: str; metrics: dict; hyperparams: dict; is_active: bool
    created_at: str; activated_at: Optional[str]

class ModelListResponse(BaseModel):
    models: list[ModelMeta]

# ── Analysis ──────────────────────────────────────────────────
class ChampionInDraft(BaseModel):
    name: str; role: Role

class DraftTeam(BaseModel):
    champions: list[ChampionInDraft] = Field(..., min_length=1, max_length=5)

class DraftAnalysisRequest(BaseModel):
    team_blue: DraftTeam; team_red: DraftTeam; model_id: Optional[int] = None

class DraftAnalysisResponse(BaseModel):
    blue_win_probability: float = Field(..., ge=0, le=1)
    red_win_probability: float = Field(..., ge=0, le=1)
    blue_synergies: list[str]; red_synergies: list[str]
    blue_counters: list[str]; red_counters: list[str]
    win_conditions: dict; tips: list[str]; model_version: Optional[str]

class BuildAnalysisRequest(BaseModel):
    champion: str; role: Optional[Role] = None
    items: list[str] = Field(..., min_length=1)
    enemy_composition: Optional[list[str]] = None
    model_id: Optional[int] = None

class AlternativeItem(BaseModel):
    replaces: str; suggested_item: str; reason: str

class BuildAnalysisResponse(BaseModel):
    champion: str; items: list[str]; score: int = Field(..., ge=0, le=100)
    win_rate_with_build: Optional[float]; strengths: list[str]
    weaknesses: list[str]; alternative_items: list[AlternativeItem]
    model_version: Optional[str]

class PlayerAnalysisRequest(BaseModel):
    summoner_id: str; champion: Optional[str] = None
    role: Optional[Role] = None
    last_n_games: int = Field(20, ge=1, le=200)
    match_type: Optional[str] = None

class PerformanceMetric(BaseModel):
    value: float; tier_average: float; percentile: float = Field(..., ge=0, le=100)

class ImprovementTip(BaseModel):
    category: TipCategory; tip: str; impact: ImpactLevel
    related_metric: Optional[str] = None

class PlayerAnalysisResponse(BaseModel):
    summoner_id: str; matches_analyzed: int; champion: Optional[str]
    role: Optional[str]; win_rate: float
    kda: Optional[PerformanceMetric]; avg_damage: Optional[PerformanceMetric]
    avg_vision: Optional[PerformanceMetric]; avg_gold_pm: Optional[PerformanceMetric]
    obj_participation: Optional[PerformanceMetric]
    tips: list[ImprovementTip]; model_version: Optional[str]

class GameAnalysisRequest(BaseModel):
    game_id: Optional[str] = None; raw_data: Optional[dict] = None
    model_id: Optional[int] = None; concern: Concern = Concern.PERFORMANCE

class PlayerOutcome(BaseModel):
    puuid: str; champion: str; role: str; win: bool; kda: float
    damage: int; gold: int; cs: int; vision: int
    performance_score: Optional[float] = None
    highlights: list[str] = []; lowlights: list[str] = []

class GameAnalysisResponse(BaseModel):
    game_id: str; patch: Optional[str]; duration_sec: Optional[int]
    winner: Optional[str]; players: list[PlayerOutcome]
    team_synergies: dict; key_moments: list[str]; model_version: Optional[str]

class ChampionAnalysisRequest(BaseModel):
    champion: str; position: Optional[str] = None; patch: Optional[str] = None
    match_type: Optional[str] = None; model_id: Optional[int] = None

class ChampionStats(BaseModel):
    champion: str; position: Optional[str]; win_rate: float; pick_rate: float
    total_games: int; avg_kda: float; avg_damage: float; avg_gold: float
    avg_cs: float; avg_vision: float; best_items: list[str]
    best_runes: list[str]; counters: list[str]; countered_by: list[str]

class ChampionAnalysisResponse(BaseModel):
    champion: str; stats: ChampionStats; tier: str; model_version: Optional[str]
'@ | Set-Content "$base\core\schemas.py" -Encoding UTF8

# ── registry/client.py ────────────────────────────────────────
@'
import os, pickle, threading
from typing import Optional
from analysis_api.core.config import cfg
from analysis_api.core import db

class RegistryClient:
    def __init__(self, concern):
        self._concern = concern; self._artifact = None; self._version = None
        self._lock = threading.Lock(); self._stop = threading.Event()
        db.init_db()
        self._load()
        self._start_watcher()

    def get_model(self):
        with self._lock:
            if self._artifact is None:
                raise RuntimeError(f"No active model for concern='{self._concern}'. Train one via POST /training/jobs.")
            return self._artifact

    def current_version(self):
        with self._lock:
            return self._version

    def is_ready(self):
        with self._lock:
            return self._artifact is not None

    def _load(self):
        row = db.get_active_model(self._concern)
        if not row: return
        path = row.get("file_path")
        if not path or not os.path.exists(path): return
        try:
            with open(path, "rb") as f:
                art = pickle.load(f)
            with self._lock:
                self._artifact = art
                self._version = row["version"]
        except Exception as e:
            print(f"[RegistryClient:{self._concern}] Load failed: {e}")

    def _start_watcher(self):
        def loop():
            while not self._stop.wait(timeout=cfg.MODEL_RELOAD_INTERVAL):
                row = db.get_active_model(self._concern)
                if row and row.get("version") != self._version:
                    self._load()
        threading.Thread(target=loop, daemon=True, name=f"watcher-{self._concern}").start()

    def stop(self):
        self._stop.set()

_clients: dict = {}
_lock = threading.Lock()

def get_client(concern):
    with _lock:
        if concern not in _clients:
            _clients[concern] = RegistryClient(concern)
        return _clients[concern]
'@ | Set-Content "$base\registry\client.py" -Encoding UTF8

# ── training/train_draft.py ───────────────────────────────────
@'
import os, pickle
from datetime import datetime, timezone
from analysis_api.core import db
from analysis_api.core.config import cfg

def _v():
    now = datetime.now(timezone.utc)
    return f"{now.strftime('%Y-W%W')}-{now.strftime('%Y%m%dT%H%M%S')}"

def train(job):
    report = job.update_progress
    filters = getattr(job, "filters", {})
    try:
        from analysis_api.training.data_loader import load_draft_data
        X, y, mlb = load_draft_data(report, filters)
    except Exception as e:
        raise RuntimeError(f"Data loading failed: {e}") from e

    from sklearn.ensemble import GradientBoostingClassifier
    from sklearn.model_selection import train_test_split
    from sklearn.metrics import accuracy_score, roc_auc_score, f1_score

    report(93, "Splitting")
    X_tr, X_te, y_tr, y_te = train_test_split(X, y, test_size=0.2, random_state=42, stratify=y)
    n = len(X_tr)
    trees = 30 if n < 5000 else 50 if n < 20000 else 100

    report(95, f"Training GBM ({trees} trees) on {n:,} rows")
    clf = GradientBoostingClassifier(n_estimators=trees, learning_rate=0.05,
                                      max_depth=4, subsample=0.8, random_state=42)
    clf.fit(X_tr, y_tr)

    report(98, "Evaluating")
    y_pred = clf.predict(X_te)
    y_prob = clf.predict_proba(X_te)[:, 1]
    metrics = {
        "accuracy":      round(float(accuracy_score(y_te, y_pred)), 4),
        "roc_auc":       round(float(roc_auc_score(y_te, y_prob)), 4),
        "f1":            round(float(f1_score(y_te, y_pred)), 4),
        "train_samples": int(len(X_tr)),
        "test_samples":  int(len(X_te)),
        "n_trees":       trees,
    }
    job.metrics = metrics

    report(99, "Saving")
    v = _v()
    os.makedirs(cfg.MODELS_DIR, exist_ok=True)
    path = os.path.join(cfg.MODELS_DIR, f"draft_{v}.pkl")
    with open(path, "wb") as f:
        pickle.dump({"model": clf, "mlb": mlb}, f)

    mid = db.register_model("draft", "gbm", v, os.path.abspath(path), metrics)
    db.activate_model(mid)
    job.model_id = mid
    report(100, f"Done — accuracy={metrics['accuracy']} roc_auc={metrics['roc_auc']}")
'@ | Set-Content "$base\training\train_draft.py" -Encoding UTF8

# ── training/train_build.py ───────────────────────────────────
@'
import os, pickle
from datetime import datetime, timezone
from analysis_api.core import db
from analysis_api.core.config import cfg

def _v():
    now = datetime.now(timezone.utc)
    return f"{now.strftime('%Y-W%W')}-{now.strftime('%Y%m%dT%H%M%S')}"

def train(job):
    report = job.update_progress
    filters = getattr(job, "filters", {})
    try:
        from analysis_api.training.data_loader import load_build_data
        X, y, encoders = load_build_data(report, filters)
    except Exception as e:
        raise RuntimeError(f"Data loading failed: {e}") from e

    from sklearn.ensemble import RandomForestClassifier
    from sklearn.model_selection import train_test_split
    from sklearn.metrics import accuracy_score, roc_auc_score

    report(93, "Splitting")
    X_tr, X_te, y_tr, y_te = train_test_split(X, y, test_size=0.2, random_state=42, stratify=y)
    n = len(X_tr)
    trees = 20 if n < 5000 else 50 if n < 20000 else 100

    report(95, f"Training RF ({trees} trees) on {n:,} rows")
    clf = RandomForestClassifier(n_estimators=trees, max_depth=10,
                                  min_samples_leaf=20, random_state=42, n_jobs=-1)
    clf.fit(X_tr, y_tr)

    report(98, "Evaluating")
    y_pred = clf.predict(X_te)
    y_prob = clf.predict_proba(X_te)[:, 1]
    metrics = {
        "accuracy":      round(float(accuracy_score(y_te, y_pred)), 4),
        "roc_auc":       round(float(roc_auc_score(y_te, y_prob)), 4),
        "train_samples": int(len(X_tr)),
        "test_samples":  int(len(X_te)),
        "n_trees":       trees,
    }
    job.metrics = metrics

    report(99, "Saving")
    v = _v()
    os.makedirs(cfg.MODELS_DIR, exist_ok=True)
    path = os.path.join(cfg.MODELS_DIR, f"build_{v}.pkl")
    with open(path, "wb") as f:
        pickle.dump({"model": clf, "encoders": encoders}, f)

    mid = db.register_model("build", "random_forest", v, os.path.abspath(path), metrics)
    db.activate_model(mid)
    job.model_id = mid
    report(100, f"Done — accuracy={metrics['accuracy']}")
'@ | Set-Content "$base\training\train_build.py" -Encoding UTF8

# ── training/train_performance.py ─────────────────────────────
@'
import os, pickle
from datetime import datetime, timezone
from analysis_api.core import db
from analysis_api.core.config import cfg

def _v():
    now = datetime.now(timezone.utc)
    return f"{now.strftime('%Y-W%W')}-{now.strftime('%Y%m%dT%H%M%S')}"

def train(job):
    report = job.update_progress
    filters = getattr(job, "filters", {})
    try:
        from analysis_api.training.data_loader import load_performance_data
        X, y, encoders, percentiles = load_performance_data(report, filters)
    except Exception as e:
        raise RuntimeError(f"Data loading failed: {e}") from e

    from sklearn.ensemble import GradientBoostingClassifier
    from sklearn.pipeline import Pipeline
    from sklearn.preprocessing import StandardScaler
    from sklearn.model_selection import train_test_split
    from sklearn.metrics import accuracy_score, f1_score

    report(93, "Splitting")
    X_tr, X_te, y_tr, y_te = train_test_split(X, y, test_size=0.2, random_state=42, stratify=y)
    n = len(X_tr)
    trees = 30 if n < 5000 else 50 if n < 20000 else 100

    report(95, f"Training GBM pipeline ({trees} trees) on {n:,} rows")
    pipe = Pipeline([
        ("scaler", StandardScaler()),
        ("clf", GradientBoostingClassifier(n_estimators=trees, learning_rate=0.05,
                                            max_depth=4, subsample=0.8, random_state=42))
    ])
    pipe.fit(X_tr, y_tr)

    report(98, "Evaluating")
    y_pred = pipe.predict(X_te)
    metrics = {
        "accuracy":      round(float(accuracy_score(y_te, y_pred)), 4),
        "f1_weighted":   round(float(f1_score(y_te, y_pred, average="weighted")), 4),
        "train_samples": int(len(X_tr)),
        "test_samples":  int(len(X_te)),
        "n_trees":       trees,
    }
    job.metrics = metrics

    report(99, "Saving")
    v = _v()
    os.makedirs(cfg.MODELS_DIR, exist_ok=True)
    path = os.path.join(cfg.MODELS_DIR, f"performance_{v}.pkl")
    with open(path, "wb") as f:
        pickle.dump({"pipeline": pipe, "encoders": encoders, "percentiles": percentiles}, f)

    mid = db.register_model("performance", "gbm", v, os.path.abspath(path), metrics)
    db.activate_model(mid)
    job.model_id = mid
    report(100, f"Done — accuracy={metrics['accuracy']}")
'@ | Set-Content "$base\training\train_performance.py" -Encoding UTF8

Write-Host "All files written successfully." -ForegroundColor Green
Write-Host "Now run: docker compose down && docker compose up --build" -ForegroundColor Cyan