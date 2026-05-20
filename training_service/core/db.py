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
    artifact      BYTEA,
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

CREATE TABLE IF NOT EXISTS datasets (
    id         TEXT        PRIMARY KEY,
    name       TEXT        NOT NULL,
    description TEXT       NOT NULL DEFAULT '',
    concern    TEXT        NOT NULL,
    filters    JSONB       NOT NULL DEFAULT '{}',
    game_count INTEGER     NOT NULL DEFAULT 0,
    row_count  INTEGER     NOT NULL DEFAULT 0,
    status     TEXT        NOT NULL DEFAULT 'registered',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    built_at   TIMESTAMPTZ,
    file_path  TEXT
);
CREATE INDEX IF NOT EXISTS idx_datasets_concern ON datasets(concern);

CREATE TABLE IF NOT EXISTS dim_champions (
    id   TEXT PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS dim_items (
    id   TEXT PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS dim_runes (
    id   TEXT PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS dim_players (
    puuid TEXT PRIMARY KEY,
    name  TEXT,
    tag   TEXT
);

CREATE TABLE IF NOT EXISTS matches (
    match_id   TEXT PRIMARY KEY,
    patch      TEXT,
    duration   INTEGER,
    timestamp  BIGINT,
    match_type TEXT
);

CREATE TABLE IF NOT EXISTS player_stats (
    match_id    TEXT NOT NULL REFERENCES matches(match_id) ON DELETE CASCADE,
    puuid       TEXT NOT NULL,
    champion_id TEXT,
    team_id     TEXT,
    win         INTEGER,
    position    TEXT,
    kills       INTEGER,
    deaths      INTEGER,
    assists     INTEGER,
    gold        INTEGER,
    cs          INTEGER,
    dmg_champs  INTEGER,
    vision      INTEGER,
    kda         DOUBLE PRECISION,
    kp          DOUBLE PRECISION,
    summ1       TEXT,
    summ2       TEXT,
    PRIMARY KEY (match_id, puuid)
);
CREATE INDEX IF NOT EXISTS idx_player_stats_champ ON player_stats(champion_id);
CREATE INDEX IF NOT EXISTS idx_player_stats_puuid ON player_stats(puuid);
CREATE INDEX IF NOT EXISTS idx_player_stats_position ON player_stats(position);

CREATE TABLE IF NOT EXISTS team_stats (
    match_id     TEXT NOT NULL REFERENCES matches(match_id) ON DELETE CASCADE,
    team_id      TEXT NOT NULL,
    win          INTEGER,
    baron        INTEGER,
    dragon       INTEGER,
    tower        INTEGER,
    inhibitor    INTEGER,
    horde        INTEGER,
    first_blood  INTEGER,
    first_tower  INTEGER,
    first_dragon INTEGER,
    PRIMARY KEY (match_id, team_id)
);

CREATE TABLE IF NOT EXISTS bans (
    match_id    TEXT NOT NULL REFERENCES matches(match_id) ON DELETE CASCADE,
    team_id     TEXT,
    champion_id TEXT,
    pick_turn   INTEGER
);
CREATE INDEX IF NOT EXISTS idx_bans_match ON bans(match_id);

CREATE TABLE IF NOT EXISTS player_items (
    match_id TEXT NOT NULL,
    puuid    TEXT NOT NULL,
    item_id  TEXT,
    slot     INTEGER,
    FOREIGN KEY (match_id, puuid) REFERENCES player_stats(match_id, puuid) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_player_items_player ON player_items(match_id, puuid);
CREATE INDEX IF NOT EXISTS idx_player_items_item ON player_items(item_id);

CREATE TABLE IF NOT EXISTS player_runes (
    match_id TEXT NOT NULL,
    puuid    TEXT NOT NULL,
    rune_id  TEXT,
    FOREIGN KEY (match_id, puuid) REFERENCES player_stats(match_id, puuid) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_player_runes_player ON player_runes(match_id, puuid);
CREATE INDEX IF NOT EXISTS idx_player_runes_rune ON player_runes(rune_id);
"""


def init_db():
    cfg.ensure_dirs()
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(_SCHEMA)
            cur.execute("ALTER TABLE models ADD COLUMN IF NOT EXISTS artifact BYTEA")


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


# ── Deployed league dataset tables ────────────────────────────────────────────

_DIMENSION_COLUMNS = {
    "dim_champions": ("id", "name"),
    "dim_items": ("id", "name"),
    "dim_runes": ("id", "name"),
    "dim_players": ("puuid", "name", "tag"),
}


def _pick(row: dict, key: str, default=None):
    return row[key] if key in row and row[key] is not None else default


def _as_int_bool(value) -> int:
    if isinstance(value, str):
        return 1 if value.lower() in {"1", "true", "t", "yes"} else 0
    return int(bool(value))


def upsert_dimension_rows(table: str, rows: list[dict]) -> int:
    columns = _DIMENSION_COLUMNS.get(table)
    if columns is None:
        raise ValueError(f"Unsupported dimension table: {table}")
    if not rows:
        return 0

    conflict_col = columns[0]
    update_cols = [c for c in columns[1:]]
    placeholders = ",".join(["%s"] * len(columns))
    update_sql = ", ".join(f"{c}=EXCLUDED.{c}" for c in update_cols)
    query = (
        f"INSERT INTO {table} ({','.join(columns)}) VALUES ({placeholders}) "
        f"ON CONFLICT ({conflict_col}) DO UPDATE SET {update_sql}"
    )
    values = [tuple(_pick(row, c) for c in columns) for row in rows]

    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.executemany(query, values)
    return len(values)


def upsert_league_match(raw: dict) -> None:
    """Persist one normalized league match into the deployed ML database."""
    match_id = _pick(raw, "match_id") or _pick(raw, "matchId") or _pick(raw, "id")
    if not match_id:
        raise ValueError("League match is missing match_id")

    participants = raw.get("participants") or []
    team_rows = raw.get("teams") or raw.get("team_stats") or []
    bans = raw.get("bans") or []

    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO matches (match_id, patch, duration, timestamp, match_type)
                VALUES (%s,%s,%s,%s,%s)
                ON CONFLICT (match_id) DO UPDATE SET
                    patch=EXCLUDED.patch,
                    duration=EXCLUDED.duration,
                    timestamp=EXCLUDED.timestamp,
                    match_type=EXCLUDED.match_type
                """,
                (
                    str(match_id),
                    _pick(raw, "patch") or _pick(raw, "gameVersion"),
                    _pick(raw, "duration")
                    or _pick(raw, "duration_sec")
                    or _pick(raw, "gameDuration"),
                    _pick(raw, "timestamp"),
                    _pick(raw, "match_type")
                    or _pick(raw, "gameType")
                    or _pick(raw, "queueType"),
                ),
            )

            cur.execute("DELETE FROM player_items WHERE match_id=%s", (str(match_id),))
            cur.execute("DELETE FROM player_runes WHERE match_id=%s", (str(match_id),))
            cur.execute("DELETE FROM bans WHERE match_id=%s", (str(match_id),))
            cur.execute("DELETE FROM team_stats WHERE match_id=%s", (str(match_id),))

            for p in participants:
                puuid = _pick(p, "puuid")
                if not puuid:
                    continue
                cur.execute(
                    """
                    INSERT INTO dim_players (puuid, name, tag)
                    VALUES (%s,%s,%s)
                    ON CONFLICT (puuid) DO UPDATE SET
                        name=COALESCE(EXCLUDED.name, dim_players.name),
                        tag=COALESCE(EXCLUDED.tag, dim_players.tag)
                    """,
                    (
                        str(puuid),
                        _pick(p, "name")
                        or _pick(p, "riotIdGameName")
                        or _pick(p, "summonerName"),
                        _pick(p, "tag") or _pick(p, "riotIdTagline"),
                    ),
                )
                cur.execute(
                    """
                    INSERT INTO player_stats
                        (match_id, puuid, champion_id, team_id, win, position,
                         kills, deaths, assists, gold, cs, dmg_champs, vision,
                         kda, kp, summ1, summ2)
                    VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
                    ON CONFLICT (match_id, puuid) DO UPDATE SET
                        champion_id=EXCLUDED.champion_id,
                        team_id=EXCLUDED.team_id,
                        win=EXCLUDED.win,
                        position=EXCLUDED.position,
                        kills=EXCLUDED.kills,
                        deaths=EXCLUDED.deaths,
                        assists=EXCLUDED.assists,
                        gold=EXCLUDED.gold,
                        cs=EXCLUDED.cs,
                        dmg_champs=EXCLUDED.dmg_champs,
                        vision=EXCLUDED.vision,
                        kda=EXCLUDED.kda,
                        kp=EXCLUDED.kp,
                        summ1=EXCLUDED.summ1,
                        summ2=EXCLUDED.summ2
                    """,
                    (
                        str(match_id),
                        str(puuid),
                        str(_pick(p, "champion_id") or _pick(p, "championId") or ""),
                        str(_pick(p, "team_id") or _pick(p, "teamId") or ""),
                        _as_int_bool(_pick(p, "win", 0)),
                        _pick(p, "position") or _pick(p, "teamPosition"),
                        _pick(p, "kills", 0),
                        _pick(p, "deaths", 0),
                        _pick(p, "assists", 0),
                        _pick(p, "gold") or _pick(p, "goldEarned", 0),
                        _pick(p, "cs") or _pick(p, "totalMinionsKilled", 0),
                        _pick(p, "dmg_champs")
                        or _pick(p, "totalDamageDealtToChampions")
                        or _pick(p, "totalDamageDealt", 0),
                        _pick(p, "vision")
                        or _pick(p, "visionScore")
                        or _pick(p, "wardsPlaced", 0),
                        _pick(p, "kda", 0.0),
                        _pick(p, "kp", 0.0),
                        _pick(p, "summ1"),
                        _pick(p, "summ2"),
                    ),
                )

                for item in p.get("items") or []:
                    cur.execute(
                        "INSERT INTO player_items (match_id, puuid, item_id, slot) VALUES (%s,%s,%s,%s)",
                        (
                            str(match_id),
                            str(puuid),
                            str(_pick(item, "item_id") or _pick(item, "itemId") or ""),
                            _pick(item, "slot"),
                        ),
                    )
                for rune in p.get("runes") or []:
                    cur.execute(
                        "INSERT INTO player_runes (match_id, puuid, rune_id) VALUES (%s,%s,%s)",
                        (
                            str(match_id),
                            str(puuid),
                            str(_pick(rune, "rune_id") or _pick(rune, "runeId") or ""),
                        ),
                    )

            for t in team_rows:
                cur.execute(
                    """
                    INSERT INTO team_stats
                        (match_id, team_id, win, baron, dragon, tower, inhibitor,
                         horde, first_blood, first_tower, first_dragon)
                    VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
                    ON CONFLICT (match_id, team_id) DO UPDATE SET
                        win=EXCLUDED.win,
                        baron=EXCLUDED.baron,
                        dragon=EXCLUDED.dragon,
                        tower=EXCLUDED.tower,
                        inhibitor=EXCLUDED.inhibitor,
                        horde=EXCLUDED.horde,
                        first_blood=EXCLUDED.first_blood,
                        first_tower=EXCLUDED.first_tower,
                        first_dragon=EXCLUDED.first_dragon
                    """,
                    (
                        str(match_id),
                        str(_pick(t, "team_id") or _pick(t, "teamId") or ""),
                        _pick(t, "win"),
                        _pick(t, "baron"),
                        _pick(t, "dragon"),
                        _pick(t, "tower"),
                        _pick(t, "inhibitor"),
                        _pick(t, "horde"),
                        _pick(t, "first_blood"),
                        _pick(t, "first_tower"),
                        _pick(t, "first_dragon"),
                    ),
                )

            for ban in bans:
                cur.execute(
                    "INSERT INTO bans (match_id, team_id, champion_id, pick_turn) VALUES (%s,%s,%s,%s)",
                    (
                        str(match_id),
                        str(_pick(ban, "team_id") or _pick(ban, "teamId") or ""),
                        str(
                            _pick(ban, "champion_id") or _pick(ban, "championId") or ""
                        ),
                        _pick(ban, "pick_turn") or _pick(ban, "pickTurn"),
                    ),
                )


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
    artifact = None
    try:
        with open(file_path, "rb") as f:
            artifact = f.read()
    except OSError:
        artifact = None

    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO models
                    (concern,algorithm,dataset_id,version,file_path,artifact,metrics,
                     hyperparams,feature_names,is_active,created_at)
                VALUES (%s,%s,%s,%s,%s,%s,%s::jsonb,%s::jsonb,%s::jsonb,FALSE,now())
                RETURNING id
                """,
                (
                    concern,
                    algorithm,
                    dataset_id,
                    version,
                    file_path,
                    artifact,
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


# ── Datasets ─────────────────────────────────────────────────────────────────


def insert_dataset(
    dataset_id: str, name: str, concern: str, filters: dict, description: str = ""
):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO datasets (id,name,description,concern,filters,created_at)
                VALUES (%s,%s,%s,%s,%s::jsonb,now())
                """,
                (dataset_id, name, description, concern, json.dumps(filters or {})),
            )


def get_dataset(dataset_id: str) -> dict | None:
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT * FROM datasets WHERE id=%s", (dataset_id,))
            d = _one(cur)
    return _parse_dataset(d) if d else None


def list_datasets(concern: str | None = None) -> list[dict]:
    params = []
    where = ""
    if concern:
        where = "WHERE concern=%s"
        params.append(concern)
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                f"SELECT * FROM datasets {where} ORDER BY created_at DESC", params
            )
            rows = _all(cur)
    return [_parse_dataset(d) for d in rows]


def update_dataset_status(dataset_id: str, **kwargs):
    allowed = {"status", "game_count", "row_count", "built_at", "file_path"}
    sets, params = [], []
    for key, value in kwargs.items():
        if key not in allowed:
            raise ValueError(f"Unsupported dataset field: {key}")
        sets.append(f"{key}=%s")
        params.append(value)
    if "built_at" not in kwargs and kwargs.get("status") in {"ready", "error"}:
        sets.append("built_at=now()")
    if not sets:
        return
    params.append(dataset_id)
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(f"UPDATE datasets SET {', '.join(sets)} WHERE id=%s", params)


def delete_dataset(dataset_id: str) -> bool:
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("DELETE FROM datasets WHERE id=%s", (dataset_id,))
            return cur.rowcount > 0


def count_active_jobs_for_dataset(dataset_id: str) -> int:
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT COUNT(*) FROM training_jobs WHERE dataset_id=%s AND status IN ('PENDING','RUNNING')",
                (dataset_id,),
            )
            return cur.fetchone()[0]


def _parse_dataset(d: dict) -> dict:
    v = d.get("filters")
    if isinstance(v, str):
        d["filters"] = json.loads(v) if v else {}
    elif v is None:
        d["filters"] = {}
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
        sets.append(f"{k}=%s::jsonb" if k in {"filters", "metrics"} else f"{k}=%s")
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
