"""
training_service/core/db.py

PostgreSQL-backed platform metadata database (replaces SQLite).
All placeholders use %s (psycopg2 style). No caller needs to know this —
they go through the helper functions, never raw SQL.
"""

import json
import threading
from contextlib import contextmanager
from datetime import datetime, timezone

from psycopg2.pool import ThreadedConnectionPool

from training_service.core.config import cfg

_pool: ThreadedConnectionPool | None = None
_pool_lock = threading.Lock()


def _get_pool() -> ThreadedConnectionPool:
    global _pool
    if _pool is not None:
        return _pool
    with _pool_lock:
        if _pool is None:
            _pool = ThreadedConnectionPool(
                minconn=1, maxconn=10, **cfg.PLATFORM_DB_KWARGS
            )
    return _pool


@contextmanager
def get_conn():
    pool = _get_pool()
    conn = pool.getconn()
    conn.autocommit = False
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        pool.putconn(conn)


def _cols(cur) -> list[str]:
    return [d[0] for d in cur.description]


def _one(cur) -> dict | None:
    row = cur.fetchone()
    return dict(zip(_cols(cur), row)) if row else None


def _all(cur) -> list[dict]:
    cols = _cols(cur)
    return [dict(zip(cols, r)) for r in cur.fetchall()]


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


# ── Schema ────────────────────────────────────────────────────────────────────

_SCHEMA = """
CREATE TABLE IF NOT EXISTS games (
    id           TEXT        PRIMARY KEY,
    source       TEXT        NOT NULL DEFAULT 'manual',
    patch        TEXT,
    match_type   TEXT,
    duration_sec INTEGER,
    platform     TEXT,
    raw_json     JSONB       NOT NULL,
    ingested_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_games_patch      ON games(patch);
CREATE INDEX IF NOT EXISTS idx_games_match_type ON games(match_type);
CREATE INDEX IF NOT EXISTS idx_games_source     ON games(source);

CREATE TABLE IF NOT EXISTS features (
    game_id        TEXT        NOT NULL,
    concern        TEXT        NOT NULL,
    feature_vector JSONB       NOT NULL,
    feature_names  JSONB       NOT NULL,
    schema_version TEXT        NOT NULL DEFAULT '1',
    extracted_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (game_id, concern),
    FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_features_concern ON features(concern);

CREATE TABLE IF NOT EXISTS models (
    id            SERIAL      PRIMARY KEY,
    concern       TEXT        NOT NULL,
    algorithm     TEXT        NOT NULL DEFAULT 'gbm',
    dataset_id    TEXT,
    version       TEXT        NOT NULL,
    file_path     TEXT        NOT NULL,
    metrics       JSONB       NOT NULL DEFAULT '{}',
    hyperparams   JSONB       NOT NULL DEFAULT '{}',
    feature_names JSONB,
    is_active     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    activated_at  TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_models_concern_active ON models(concern, is_active);

CREATE TABLE IF NOT EXISTS training_jobs (
    id           TEXT        PRIMARY KEY,
    concern      TEXT        NOT NULL,
    algorithm    TEXT        NOT NULL DEFAULT 'auto',
    dataset_id   TEXT,
    status       TEXT        NOT NULL DEFAULT 'PENDING',
    progress     INTEGER     NOT NULL DEFAULT 0,
    stage        TEXT        NOT NULL DEFAULT 'Queued',
    filters      JSONB       NOT NULL DEFAULT '{}',
    metrics      JSONB,
    model_id     INTEGER     REFERENCES models(id),
    error        TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at   TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_jobs_concern ON training_jobs(concern);
CREATE INDEX IF NOT EXISTS idx_jobs_status  ON training_jobs(status);
"""


def init_db():
    cfg.ensure_dirs()
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(_SCHEMA)


# ── Games ─────────────────────────────────────────────────────────────────────


def count_games() -> int:
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT COUNT(*) FROM games")
            return cur.fetchone()[0]


def insert_game(game_id: str, raw: dict, source: str = "manual"):
    patch = raw.get("patch") or raw.get("gameVersion")
    match_type = raw.get("match_type") or raw.get("gameType") or raw.get("queueType")
    duration_sec = raw.get("duration_sec") or raw.get("gameDuration")
    platform = raw.get("platform") or raw.get("platformId")
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO games (id,source,patch,match_type,duration_sec,platform,raw_json,ingested_at)
                VALUES (%s,%s,%s,%s,%s,%s,%s::jsonb,now())
                ON CONFLICT (id) DO UPDATE SET
                    source=EXCLUDED.source, patch=EXCLUDED.patch,
                    match_type=EXCLUDED.match_type, duration_sec=EXCLUDED.duration_sec,
                    platform=EXCLUDED.platform, raw_json=EXCLUDED.raw_json,
                    ingested_at=now()
                """,
                (
                    game_id,
                    source,
                    patch,
                    match_type,
                    duration_sec,
                    platform,
                    json.dumps(raw),
                ),
            )


def get_game(game_id: str) -> dict | None:
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT * FROM games WHERE id=%s", (game_id,))
            d = _one(cur)
    if d and isinstance(d.get("raw_json"), str):
        d["raw_json"] = json.loads(d["raw_json"])
    return d


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
                f"SELECT id,source,patch,match_type,duration_sec,ingested_at "
                f"FROM games {where} ORDER BY ingested_at DESC LIMIT %s OFFSET %s",
                params + [limit, offset],
            )
            rows = _all(cur)
    return rows, total


def delete_game(game_id: str) -> bool:
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("DELETE FROM games WHERE id=%s", (game_id,))
            return cur.rowcount > 0


# ── Features ──────────────────────────────────────────────────────────────────


def upsert_features(game_id, concern, vector, names, schema_version="1"):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO features (game_id,concern,feature_vector,feature_names,schema_version,extracted_at)
                VALUES (%s,%s,%s::jsonb,%s::jsonb,%s,now())
                ON CONFLICT (game_id,concern) DO UPDATE SET
                    feature_vector=EXCLUDED.feature_vector,
                    feature_names=EXCLUDED.feature_names,
                    schema_version=EXCLUDED.schema_version,
                    extracted_at=now()
                """,
                (
                    game_id,
                    concern,
                    json.dumps(vector),
                    json.dumps(names),
                    schema_version,
                ),
            )


def get_features(game_id: str, concern: str | None = None) -> list[dict]:
    with get_conn() as conn:
        with conn.cursor() as cur:
            if concern:
                cur.execute(
                    "SELECT * FROM features WHERE game_id=%s AND concern=%s",
                    (game_id, concern),
                )
            else:
                cur.execute("SELECT * FROM features WHERE game_id=%s", (game_id,))
            rows = _all(cur)
    for d in rows:
        for f in ("feature_vector", "feature_names"):
            if isinstance(d.get(f), str):
                d[f] = json.loads(d[f])
    return rows


def delete_features(game_id: str, concern: str | None = None) -> int:
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


# ── Models ────────────────────────────────────────────────────────────────────


def register_model(
    concern,
    algorithm,
    version,
    file_path,
    metrics,
    hyperparams=None,
    dataset_id=None,
    feature_names=None,
) -> int:
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO models
                    (concern,algorithm,dataset_id,version,file_path,metrics,
                     hyperparams,feature_names,is_active,created_at)
                VALUES (%s,%s,%s,%s,%s,%s::jsonb,%s::jsonb,%s::jsonb,FALSE,now())
                RETURNING id
                """,
                (
                    concern,
                    algorithm,
                    dataset_id,
                    version,
                    file_path,
                    json.dumps(metrics),
                    json.dumps(hyperparams or {}),
                    json.dumps(feature_names or []),
                ),
            )
            return cur.fetchone()[0]


def activate_model(model_id: int):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT concern FROM models WHERE id=%s", (model_id,))
            row = cur.fetchone()
            if row is None:
                raise ValueError(f"No model id={model_id}")
            cur.execute(
                "UPDATE models SET is_active=FALSE WHERE concern=%s AND is_active=TRUE",
                (row[0],),
            )
            cur.execute(
                "UPDATE models SET is_active=TRUE, activated_at=now() WHERE id=%s",
                (model_id,),
            )


def deactivate_model(model_id: int):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("UPDATE models SET is_active=FALSE WHERE id=%s", (model_id,))


def delete_model(model_id: int) -> bool:
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("DELETE FROM models WHERE id=%s", (model_id,))
            return cur.rowcount > 0


def get_active_model(concern: str) -> dict | None:
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT * FROM models WHERE concern=%s AND is_active=TRUE", (concern,)
            )
            d = _one(cur)
    return _parse_model(d) if d else None


def get_model_by_id(model_id: int) -> dict | None:
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT * FROM models WHERE id=%s", (model_id,))
            d = _one(cur)
    return _parse_model(d) if d else None


def list_models(concern=None, active_only=False) -> list[dict]:
    clauses, params = [], []
    if concern:
        clauses.append("concern=%s")
        params.append(concern)
    if active_only:
        clauses.append("is_active=TRUE")
    where = ("WHERE " + " AND ".join(clauses)) if clauses else ""
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                f"SELECT * FROM models {where} ORDER BY created_at DESC", params
            )
            rows = _all(cur)
    return [_parse_model(d) for d in rows]


def _parse_model(d: dict) -> dict:
    for f in ("metrics", "hyperparams", "feature_names"):
        v = d.get(f)
        if isinstance(v, str):
            d[f] = json.loads(v) if v else {}
        elif v is None:
            d[f] = {}
    d["is_active"] = bool(d.get("is_active"))
    return d


# ── Training jobs ─────────────────────────────────────────────────────────────


def create_job(job_id, concern, algorithm="auto", dataset_id=None, filters=None):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "INSERT INTO training_jobs (id,concern,algorithm,dataset_id,filters,created_at) "
                "VALUES (%s,%s,%s,%s,%s::jsonb,now())",
                (job_id, concern, algorithm, dataset_id, json.dumps(filters or {})),
            )


def update_job(job_id: str, **kwargs):
    if not kwargs:
        return
    sets, params = [], []
    for k, v in kwargs.items():
        sets.append(f"{k}=%s")
        params.append(json.dumps(v) if isinstance(v, (dict, list)) else v)
    params.append(job_id)
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                f"UPDATE training_jobs SET {', '.join(sets)} WHERE id=%s", params
            )


def get_job(job_id: str) -> dict | None:
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT * FROM training_jobs WHERE id=%s", (job_id,))
            d = _one(cur)
    return _parse_job(d) if d else None


def list_jobs(concern=None, status=None, limit=100) -> list[dict]:
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
            rows = _all(cur)
    return [_parse_job(d) for d in rows]


def _parse_job(d: dict) -> dict:
    for f in ("filters", "metrics"):
        v = d.get(f)
        if isinstance(v, str):
            d[f] = json.loads(v) if v else {}
        elif v is None:
            d[f] = {}
    return d
