import json
import threading
from contextlib import contextmanager
from datetime import datetime, timezone

from psycopg2.extras import Json
from psycopg2.pool import ThreadedConnectionPool

from training_service.core.config import cfg

_POOL: ThreadedConnectionPool | None = None
_POOL_LOCK = threading.Lock()


def _get_pool() -> ThreadedConnectionPool:
    global _POOL
    if _POOL is None:
        with _POOL_LOCK:
            if _POOL is None:
                _POOL = ThreadedConnectionPool(
                    minconn=1,
                    maxconn=10,
                    dsn=cfg.PLATFORM_DB_DSN,
                )
    return _POOL


@contextmanager
def get_conn():
    pool = _get_pool()
    conn = pool.getconn()
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        pool.putconn(conn)


def now_iso():
    return datetime.now(timezone.utc).isoformat()


def _cols(cursor):
    return [c.name for c in cursor.description]


def _one(cursor):
    row = cursor.fetchone()
    if row is None:
        return None
    return dict(zip(_cols(cursor), row))


def _many(cursor):
    cols = _cols(cursor)
    return [dict(zip(cols, row)) for row in cursor.fetchall()]


def _json(v, default=None):
    if v is None:
        return {} if default is None else default
    if isinstance(v, str):
        return json.loads(v)
    return v


def _iso(v):
    return v.isoformat() if hasattr(v, "isoformat") else v


def _normalize_timestamps(d):
    for key, value in list(d.items()):
        if key.endswith("_at") and value is not None:
            d[key] = _iso(value)
    return d


_SCHEMA = """
CREATE TABLE IF NOT EXISTS games (
    id           TEXT PRIMARY KEY,
    source       TEXT NOT NULL DEFAULT 'manual',
    patch        TEXT,
    match_type   TEXT,
    duration_sec INTEGER,
    platform     TEXT,
    raw_json     JSONB NOT NULL,
    ingested_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_games_patch       ON games(patch);
CREATE INDEX IF NOT EXISTS idx_games_match_type  ON games(match_type);
CREATE INDEX IF NOT EXISTS idx_games_source      ON games(source);

CREATE TABLE IF NOT EXISTS features (
    game_id        TEXT NOT NULL,
    concern        TEXT NOT NULL,
    feature_vector JSONB NOT NULL,
    feature_names  JSONB NOT NULL,
    schema_version TEXT NOT NULL DEFAULT '1',
    extracted_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (game_id, concern),
    FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_features_concern ON features(concern);

CREATE TABLE IF NOT EXISTS models (
    id            SERIAL PRIMARY KEY,
    concern       TEXT NOT NULL,
    algorithm     TEXT NOT NULL DEFAULT 'gbm',
    dataset_id    TEXT,
    version       TEXT NOT NULL,
    file_path     TEXT NOT NULL,
    metrics       JSONB NOT NULL DEFAULT '{}',
    hyperparams   JSONB NOT NULL DEFAULT '{}',
    feature_names JSONB,
    is_active     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    activated_at  TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_models_concern_active ON models(concern, is_active);

CREATE TABLE IF NOT EXISTS training_jobs (
    id           TEXT PRIMARY KEY,
    concern      TEXT NOT NULL,
    algorithm    TEXT NOT NULL DEFAULT 'auto',
    dataset_id   TEXT,
    status       TEXT NOT NULL DEFAULT 'PENDING',
    progress     INTEGER NOT NULL DEFAULT 0,
    stage        TEXT NOT NULL DEFAULT 'Queued',
    filters      JSONB NOT NULL DEFAULT '{}',
    metrics      JSONB,
    model_id     INTEGER,
    error        TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at   TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    FOREIGN KEY (model_id) REFERENCES models(id)
);
CREATE INDEX IF NOT EXISTS idx_jobs_concern ON training_jobs(concern);
CREATE INDEX IF NOT EXISTS idx_jobs_status  ON training_jobs(status);
"""


def init_db():
    cfg.ensure_dirs()
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(_SCHEMA)


def count_games():
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT COUNT(*) FROM games")
            return cur.fetchone()[0]


def insert_game(game_id, raw, source="manual"):
    patch = raw.get("patch") or raw.get("gameVersion")
    match_type = raw.get("match_type") or raw.get("gameType") or raw.get("queueType")
    duration_sec = raw.get("duration_sec") or raw.get("gameDuration")
    platform = raw.get("platform") or raw.get("platformId")
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO games
                    (id, source, patch, match_type, duration_sec, platform, raw_json, ingested_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s, now())
                ON CONFLICT (id) DO UPDATE SET
                    source = EXCLUDED.source,
                    patch = EXCLUDED.patch,
                    match_type = EXCLUDED.match_type,
                    duration_sec = EXCLUDED.duration_sec,
                    platform = EXCLUDED.platform,
                    raw_json = EXCLUDED.raw_json,
                    ingested_at = now()
                """,
                (game_id, source, patch, match_type, duration_sec, platform, Json(raw)),
            )


def get_game(game_id):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT * FROM games WHERE id=%s", (game_id,))
            row = _one(cur)
    if row is None:
        return None
    row["raw_json"] = _json(row["raw_json"])
    return _normalize_timestamps(row)


def list_games(source=None, patch=None, match_type=None, limit=50, offset=0):
    clauses, params = [], []
    if source:
        clauses.append("source=%s")
        params.append(source)
    if patch:
        clauses.append("patch=%s")
        params.append(patch)
    if match_type:
        clauses.append("match_type=%s")
        params.append(match_type)
    where = ("WHERE " + " AND ".join(clauses)) if clauses else ""
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(f"SELECT COUNT(*) FROM games {where}", params)
            total = cur.fetchone()[0]
            cur.execute(
                "SELECT id,source,patch,match_type,duration_sec,ingested_at "
                f"FROM games {where} ORDER BY ingested_at DESC LIMIT %s OFFSET %s",
                params + [limit, offset],
            )
            rows = _many(cur)
    return [_normalize_timestamps(r) for r in rows], total


def delete_game(game_id):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("DELETE FROM games WHERE id=%s", (game_id,))
            return cur.rowcount > 0


def upsert_features(game_id, concern, vector, names, schema_version="1"):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO features
                    (game_id, concern, feature_vector, feature_names, schema_version, extracted_at)
                VALUES (%s, %s, %s, %s, %s, now())
                ON CONFLICT (game_id, concern) DO UPDATE SET
                    feature_vector = EXCLUDED.feature_vector,
                    feature_names = EXCLUDED.feature_names,
                    schema_version = EXCLUDED.schema_version,
                    extracted_at = now()
                """,
                (game_id, concern, Json(vector), Json(names), schema_version),
            )


def get_features(game_id, concern=None):
    with get_conn() as conn:
        with conn.cursor() as cur:
            if concern:
                cur.execute(
                    "SELECT * FROM features WHERE game_id=%s AND concern=%s",
                    (game_id, concern),
                )
            else:
                cur.execute("SELECT * FROM features WHERE game_id=%s", (game_id,))
            rows = _many(cur)
    result = []
    for row in rows:
        row["feature_vector"] = _json(row["feature_vector"], [])
        row["feature_names"] = _json(row["feature_names"], [])
        result.append(_normalize_timestamps(row))
    return result


def delete_features(game_id, concern=None):
    with get_conn() as conn:
        with conn.cursor() as cur:
            if concern:
                cur.execute(
                    "DELETE FROM features WHERE game_id=%s AND concern=%s",
                    (game_id, concern),
                )
            else:
                cur.execute("DELETE FROM features WHERE game_id=%s", (game_id,))
            return cur.rowcount


def insert_dataset(ds_id, name, concern, filters, description=""):
    raise NotImplementedError("Dataset metadata storage is out of scope for ml-db.")


def update_dataset_status(ds_id, status, game_count=0, row_count=0, file_path=None):
    raise NotImplementedError("Dataset metadata storage is out of scope for ml-db.")


def get_dataset(ds_id):
    return None


def list_datasets(concern=None):
    return []


def delete_dataset(ds_id):
    return False


def register_model(
    concern,
    algorithm,
    version,
    file_path,
    metrics,
    hyperparams=None,
    dataset_id=None,
    feature_names=None,
):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO models
                    (concern, algorithm, dataset_id, version, file_path, metrics,
                     hyperparams, feature_names, is_active, created_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, FALSE, now())
                RETURNING id
                """,
                (
                    concern,
                    algorithm,
                    dataset_id,
                    version,
                    file_path,
                    Json(metrics),
                    Json(hyperparams or {}),
                    Json(feature_names or []),
                ),
            )
            return cur.fetchone()[0]


def activate_model(model_id):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT concern FROM models WHERE id=%s", (model_id,))
            row = cur.fetchone()
            if row is None:
                raise ValueError(f"No model id={model_id}")
            concern = row[0]
            cur.execute(
                "UPDATE models SET is_active=FALSE WHERE concern=%s AND is_active=TRUE",
                (concern,),
            )
            cur.execute(
                "UPDATE models SET is_active=TRUE, activated_at=now() WHERE id=%s",
                (model_id,),
            )


def deactivate_model(model_id):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("UPDATE models SET is_active=FALSE WHERE id=%s", (model_id,))


def delete_model(model_id):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("DELETE FROM models WHERE id=%s", (model_id,))
            return cur.rowcount > 0


def _model_row(row):
    if row is None:
        return None
    for field in ("metrics", "hyperparams", "feature_names"):
        row[field] = _json(row[field], [] if field == "feature_names" else {})
    row["is_active"] = bool(row["is_active"])
    return _normalize_timestamps(row)


def get_active_model(concern):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT * FROM models WHERE concern=%s AND is_active=TRUE", (concern,)
            )
            row = _one(cur)
    return _model_row(row)


def get_model_by_id(model_id):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT * FROM models WHERE id=%s", (model_id,))
            row = _one(cur)
    return _model_row(row)


def list_models(concern=None, active_only=False):
    clauses, params = [], []
    if concern:
        clauses.append("concern=%s")
        params.append(concern)
    if active_only:
        clauses.append("is_active=TRUE")
    where = ("WHERE " + " AND ".join(clauses)) if clauses else ""
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(f"SELECT * FROM models {where} ORDER BY created_at DESC", params)
            rows = _many(cur)
    return [_model_row(r) for r in rows]


def create_job(job_id, concern, algorithm="auto", dataset_id=None, filters=None):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO training_jobs
                    (id, concern, algorithm, dataset_id, filters, created_at)
                VALUES (%s, %s, %s, %s, %s, now())
                """,
                (job_id, concern, algorithm, dataset_id, Json(filters or {})),
            )


_JOB_COLUMNS = {
    "concern",
    "algorithm",
    "dataset_id",
    "status",
    "progress",
    "stage",
    "filters",
    "metrics",
    "model_id",
    "error",
    "started_at",
    "completed_at",
}


def update_job(job_id, **kwargs):
    sets, params = [], []
    for key, value in kwargs.items():
        if key not in _JOB_COLUMNS:
            raise ValueError(f"Unknown training_jobs column '{key}'")
        sets.append(f"{key}=%s")
        params.append(Json(value) if isinstance(value, (dict, list)) else value)
    if not sets:
        return
    params.append(job_id)
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                f"UPDATE training_jobs SET {', '.join(sets)} WHERE id=%s",
                params,
            )


def _job_row(row):
    if row is None:
        return None
    for field in ("filters", "metrics"):
        row[field] = _json(row[field])
    return _normalize_timestamps(row)


def get_job(job_id):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT * FROM training_jobs WHERE id=%s", (job_id,))
            row = _one(cur)
    return _job_row(row)


def list_jobs(concern=None, status=None, limit=100):
    clauses, params = [], []
    if concern:
        clauses.append("concern=%s")
        params.append(concern)
    if status:
        clauses.append("status=%s")
        params.append(status)
    where = ("WHERE " + " AND ".join(clauses)) if clauses else ""
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                f"SELECT * FROM training_jobs {where} ORDER BY created_at DESC LIMIT %s",
                params + [limit],
            )
            rows = _many(cur)
    return [_job_row(r) for r in rows]
