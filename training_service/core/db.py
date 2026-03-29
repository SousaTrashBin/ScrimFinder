import json
import sqlite3
from contextlib import contextmanager
from datetime import datetime, timezone

from training_service.core.config import cfg


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
    patch = raw.get("patch") or raw.get("gameVersion")
    match_type = raw.get("match_type") or raw.get("gameType") or raw.get("queueType")
    duration_sec = raw.get("duration_sec") or raw.get("gameDuration")
    platform = raw.get("platform") or raw.get("platformId")
    with get_conn() as conn:
        conn.execute(
            "INSERT OR REPLACE INTO games (id,source,patch,match_type,duration_sec,platform,raw_json,ingested_at) VALUES (?,?,?,?,?,?,?,?)",
            (game_id, source, patch, match_type, duration_sec, platform, json.dumps(raw), now_iso()),
        )


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
    if source:
        clauses.append("source=?")
        params.append(source)
    if patch:
        clauses.append("patch=?")
        params.append(patch)
    if match_type:
        clauses.append("match_type=?")
        params.append(match_type)
    where = ("WHERE " + " AND ".join(clauses)) if clauses else ""
    with get_conn() as conn:
        total = conn.execute(f"SELECT COUNT(*) FROM games {where}", params).fetchone()[0]
        rows = conn.execute(
            f"SELECT id,source,patch,match_type,duration_sec,ingested_at FROM games {where} ORDER BY ingested_at DESC LIMIT ? OFFSET ?",
            params + [limit, offset],
        ).fetchall()
    return [dict(r) for r in rows], total


def upsert_features(game_id, concern, vector, names, schema_version="1"):
    with get_conn() as conn:
        conn.execute(
            "INSERT OR REPLACE INTO features (game_id,concern,feature_vector,feature_names,schema_version,extracted_at) VALUES (?,?,?,?,?,?)",
            (game_id, concern, json.dumps(vector), json.dumps(names), schema_version, now_iso()),
        )


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
        d["feature_names"] = json.loads(d["feature_names"])
        result.append(d)
    return result


def insert_dataset(ds_id, name, concern, filters, description=""):
    with get_conn() as conn:
        conn.execute(
            "INSERT INTO datasets (id,name,description,concern,filters,status,created_at) VALUES (?,?,?,?,?,'pending',?)",
            (ds_id, name, description, concern, json.dumps(filters), now_iso()),
        )


def update_dataset_status(ds_id, status, game_count=0, row_count=0, file_path=None):
    with get_conn() as conn:
        conn.execute(
            "UPDATE datasets SET status=?,game_count=?,row_count=?,file_path=?,built_at=? WHERE id=?",
            (status, game_count, row_count, file_path, now_iso(), ds_id),
        )


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
            rows = conn.execute(
                "SELECT * FROM datasets WHERE concern=? ORDER BY created_at DESC", (concern,)
            ).fetchall()
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


def register_model(
    concern, algorithm, version, file_path, metrics, hyperparams=None, dataset_id=None, feature_names=None
):
    with get_conn() as conn:
        cur = conn.execute(
            "INSERT INTO models (concern,algorithm,dataset_id,version,file_path,metrics,hyperparams,feature_names,is_active,created_at) VALUES (?,?,?,?,?,?,?,?,0,?)",
            (
                concern,
                algorithm,
                dataset_id,
                version,
                file_path,
                json.dumps(metrics),
                json.dumps(hyperparams or {}),
                json.dumps(feature_names or []),
                now_iso(),
            ),
        )
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
    if concern:
        clauses.append("concern=?")
        params.append(concern)
    if active_only:
        clauses.append("is_active=1")
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
            (job_id, concern, algorithm, dataset_id, json.dumps(filters or {}), now_iso()),
        )


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
    if concern:
        clauses.append("concern=?")
        params.append(concern)
    if status:
        clauses.append("status=?")
        params.append(status)
    where = ("WHERE " + " AND ".join(clauses)) if clauses else ""
    with get_conn() as conn:
        rows = conn.execute(
            f"SELECT * FROM training_jobs {where} ORDER BY created_at DESC LIMIT ?", params + [limit]
        ).fetchall()
    result = []
    for r in rows:
        d = dict(r)
        for f in ("filters", "metrics"):
            d[f] = json.loads(d[f]) if d[f] else {}
        result.append(d)
    return result
