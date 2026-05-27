"""
training_service/core/db.py

"""

import json
import uuid
import os
from datetime import datetime, timezone
from typing import Optional, Any, List, Dict

from google.cloud import bigquery
from google.cloud.bigquery import QueryJobConfig, ScalarQueryParameter

from training_service.core.config import cfg
from training_service.core.bq_schema import init_bq_platform

_client: Optional[bigquery.Client] = None


def get_bq_client() -> bigquery.Client:
    global _client
    if _client is None:
        _client = bigquery.Client(project=cfg.BQ_PROJECT)
    return _client


def init_db():
    """Initialize BigQuery platform schema."""
    if not cfg.BQ_PROJECT:
        raise RuntimeError("BQ_PROJECT not set")
    client = get_bq_client()
    init_bq_platform(client, cfg.BQ_PROJECT, cfg.BQ_PLATFORM_DATASET)


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def _uuid() -> str:
    return str(uuid.uuid4())


def _bq_query(sql: str, params: Optional[List[Any]] = None) -> bigquery.table.RowIterator:
    """Execute a query with optional parameters."""
    client = get_bq_client()
    job_config = QueryJobConfig()
    if params:
        job_config.query_parameters = [
            ScalarQueryParameter(f"p{i}", _bq_type(p), p)
            for i, p in enumerate(params)
        ]
        # Replace %s placeholders with @pN
        parts = sql.split("%s")
        sql = "".join(f"{part}@p{i}" for i, part in enumerate(parts[:-1])) + parts[-1]
    # Respect dataset location (EU/US) to avoid 404 "not found in location US"
    location = getattr(cfg, "BQ_LOCATION", None)
    return client.query(sql, job_config=job_config, location=location).result()

def _bq_type(value: Any) -> str:
    if isinstance(value, bool):
        return "BOOL"
    if isinstance(value, int):
        return "INT64"
    if isinstance(value, float):
        return "FLOAT64"
    if isinstance(value, bytes):
        return "BYTES"
    if isinstance(value, str):
        return "STRING"
    return "STRING"


def _row_to_dict(row: bigquery.Row) -> Dict[str, Any]:
    d = dict(row.items())
    # Parse JSON strings back to Python objects
    for key, val in d.items():
        if isinstance(val, str) and (val.startswith("{") or val.startswith("[")):
            try:
                d[key] = json.loads(val)
            except json.JSONDecodeError:
                pass
        elif isinstance(val, str) and "PARSE_JSON" in val:
            # Handle case where BQ mock returns the SQL expression as string
            # Extract the JSON part from expressions like "PARSE_JSON('{...}')"
            import re
            m = re.search(r"PARSE_JSON\('(.+?)'\)", val, re.DOTALL)
            if m:
                try:
                    d[key] = json.loads(m.group(1).replace("''", "'"))
                except json.JSONDecodeError:
                    pass
    return d


# ── Games ─────────────────────────────────────────────────────────────────


def count_games() -> int:
    sql = f"SELECT COUNT(*) as c FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.games`"
    for row in _bq_query(sql):
        return row.c
    return 0


def insert_game(game_id: str, raw: dict, source: str = "manual"):
    patch = raw.get("patch") or raw.get("gameVersion")
    match_type = raw.get("match_type") or raw.get("gameType") or raw.get("queueType")
    duration_sec = raw.get("duration_sec") or raw.get("gameDuration")
    platform = raw.get("platform") or raw.get("platformId")
    sql = f"""
    MERGE `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.games` T
    USING (SELECT @p0 as id, @p1 as source, @p2 as patch, @p3 as match_type,
                  @p4 as duration_sec, @p5 as platform, PARSE_JSON(@p6) as raw_json) S
    ON T.id = S.id
    WHEN MATCHED THEN UPDATE SET
        source = S.source, patch = S.patch, match_type = S.match_type,
        duration_sec = S.duration_sec, platform = S.platform,
        raw_json = S.raw_json, ingested_at = CURRENT_TIMESTAMP()
    WHEN NOT MATCHED THEN INSERT (id, source, patch, match_type, duration_sec, platform, raw_json, ingested_at)
    VALUES (S.id, S.source, S.patch, S.match_type, S.duration_sec, S.platform, S.raw_json, CURRENT_TIMESTAMP())
    """
    _bq_query(sql, [
        game_id, source, patch, match_type, duration_sec, platform, json.dumps(raw)
    ])


def get_game(game_id: str) -> Optional[Dict[str, Any]]:
    sql = f"SELECT * FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.games` WHERE id = @p0"
    for row in _bq_query(sql, [game_id]):
        return _row_to_dict(row)
    return None


def list_games(source=None, patch=None, match_type=None, limit=50, offset=0):
    where_clauses = []
    params = []
    if source:
        where_clauses.append("source = @p0")
        params.append(source)
    if patch:
        where_clauses.append(f"patch = @p{len(params)}")
        params.append(patch)
    if match_type:
        where_clauses.append(f"match_type = @p{len(params)}")
        params.append(match_type)
    where = " AND ".join(where_clauses)
    where_sql = f"WHERE {where}" if where else ""

    count_sql = f"SELECT COUNT(*) as c FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.games` {where_sql}"
    total = 0
    for row in _bq_query(count_sql, params):
        total = row.c

    data_sql = f"""
    SELECT id, source, patch, match_type, duration_sec, ingested_at
    FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.games`
    {where_sql}
    ORDER BY ingested_at DESC
    LIMIT {limit} OFFSET {offset}
    """
    rows = [_row_to_dict(r) for r in _bq_query(data_sql, params)]
    return rows, total


def delete_game(game_id: str) -> bool:
    sql = f"DELETE FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.games` WHERE id = @p0"
    _bq_query(sql, [game_id])
    return True


# ── Features ──────────────────────────────────────────────────────────────


def upsert_features(game_id, concern, vector, names, schema_version="1"):
    sql = f"""
    MERGE `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.features` T
    USING (SELECT @p0 as game_id, @p1 as concern) S
    ON T.game_id = S.game_id AND T.concern = S.concern
    WHEN MATCHED THEN UPDATE SET
        feature_vector = PARSE_JSON(@p2), feature_names = PARSE_JSON(@p3), schema_version = @p4, extracted_at = CURRENT_TIMESTAMP()
    WHEN NOT MATCHED THEN INSERT (game_id, concern, feature_vector, feature_names, schema_version, extracted_at)
    VALUES (@p0, @p1, PARSE_JSON(@p2), PARSE_JSON(@p3), @p4, CURRENT_TIMESTAMP())
    """
    _bq_query(sql, [game_id, concern, json.dumps(vector), json.dumps(names), schema_version])


def get_features(game_id: str, concern: Optional[str] = None) -> List[Dict[str, Any]]:
    if concern:
        sql = f"SELECT * FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.features` WHERE game_id = @p0 AND concern = @p1"
        params = [game_id, concern]
    else:
        sql = f"SELECT * FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.features` WHERE game_id = @p0"
        params = [game_id]
    return [_row_to_dict(r) for r in _bq_query(sql, params)]


def delete_features(game_id: str, concern: Optional[str] = None) -> int:
    if concern:
        sql = f"DELETE FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.features` WHERE game_id = @p0 AND concern = @p1"
        params = [game_id, concern]
    else:
        sql = f"DELETE FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.features` WHERE game_id = @p0"
        params = [game_id]
    _bq_query(sql, params)
    return 1


# ── Models ─────────────────────────────────────────────────────────────────


def register_model(concern, algorithm, version, file_path, metrics, hyperparams=None, dataset_id=None, feature_names=None) -> str:
    model_id = _uuid()
    artifact = None
    try:
        with open(file_path, "rb") as f:
            artifact = f.read()
    except OSError:
        artifact = None

    sql = f"""
    INSERT INTO `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.models`
    (id, concern, algorithm, dataset_id, version, file_path, artifact, metrics, hyperparams, feature_names, is_active, created_at)
    VALUES (@p0, @p1, @p2, @p3, @p4, @p5, @p6, PARSE_JSON(@p7), PARSE_JSON(@p8), PARSE_JSON(@p9), FALSE, CURRENT_TIMESTAMP())
    """
    _bq_query(sql, [
        model_id, concern, algorithm, dataset_id, version, file_path,
        artifact, json.dumps(metrics), json.dumps(hyperparams or {}), json.dumps(feature_names or [])
    ])
    return model_id


def activate_model(model_id: str):
    sql1 = f"""
    UPDATE `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.models`
    SET is_active = FALSE
    WHERE concern = (SELECT concern FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.models` WHERE id = @p0)
    AND is_active = TRUE
    """
    _bq_query(sql1, [model_id])

    sql2 = f"""
    UPDATE `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.models`
    SET is_active = TRUE, activated_at = CURRENT_TIMESTAMP()
    WHERE id = @p0
    """
    _bq_query(sql2, [model_id])


def deactivate_model(model_id: str):
    sql = f"UPDATE `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.models` SET is_active = FALSE WHERE id = @p0"
    _bq_query(sql, [model_id])


def delete_model(model_id: str) -> bool:
    sql = f"DELETE FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.models` WHERE id = @p0"
    _bq_query(sql, [model_id])
    return True


def get_active_model(concern: str) -> Optional[Dict[str, Any]]:
    sql = f"SELECT * FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.models` WHERE concern = @p0 AND is_active = TRUE"
    for row in _bq_query(sql, [concern]):
        return _row_to_dict(row)
    return None


def get_model_by_id(model_id: str) -> Optional[Dict[str, Any]]:
    sql = f"SELECT * FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.models` WHERE id = @p0"
    for row in _bq_query(sql, [model_id]):
        return _row_to_dict(row)
    return None


def list_models(concern=None, active_only=False) -> List[Dict[str, Any]]:
    where_clauses = []
    params = []
    if concern:
        where_clauses.append("concern = @p0")
        params.append(concern)
    if active_only:
        where_clauses.append(f"is_active = TRUE")
    where = " AND ".join(where_clauses)
    where_sql = f"WHERE {where}" if where else ""
    sql = f"SELECT * FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.models` {where_sql} ORDER BY created_at DESC"
    return [_row_to_dict(r) for r in _bq_query(sql, params)]


# ── Datasets ─────────────────────────────────────────────────────────────


def insert_dataset(dataset_id: str, name: str, concern: str, filters: dict, description: str = ""):
    sql = f"""
    INSERT INTO `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.datasets`
    (id, name, description, concern, filters, game_count, row_count, status, created_at)
    VALUES (@p0, @p1, @p2, @p3, PARSE_JSON(@p4), 0, 0, 'registered', CURRENT_TIMESTAMP())
    """
    _bq_query(sql, [dataset_id, name, description, concern, json.dumps(filters or {})])


def get_dataset(dataset_id: str) -> Optional[Dict[str, Any]]:
    sql = f"SELECT * FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.datasets` WHERE id = @p0"
    for row in _bq_query(sql, [dataset_id]):
        return _row_to_dict(row)
    return None


def list_datasets(concern: Optional[str] = None) -> List[Dict[str, Any]]:
    if concern:
        sql = f"SELECT * FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.datasets` WHERE concern = @p0 ORDER BY created_at DESC"
        params = [concern]
    else:
        sql = f"SELECT * FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.datasets` ORDER BY created_at DESC"
        params = []
    return [_row_to_dict(r) for r in _bq_query(sql, params)]


def update_dataset_status(dataset_id: str, **kwargs):
    allowed = {"status", "game_count", "row_count", "built_at", "file_path"}
    sets = []
    params = []
    for key, value in kwargs.items():
        if key not in allowed:
            raise ValueError(f"Unsupported dataset field: {key}")
        sets.append(f"{key} = @p{len(params)}")
        params.append(value)
    if not sets:
        return
    params.append(dataset_id)
    set_sql = ", ".join(sets)
    sql = f"UPDATE `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.datasets` SET {set_sql} WHERE id = @p{len(params)-1}"
    _bq_query(sql, params)


def delete_dataset(dataset_id: str) -> bool:
    sql = f"DELETE FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.datasets` WHERE id = @p0"
    _bq_query(sql, [dataset_id])
    return True


def count_active_jobs_for_dataset(dataset_id: str) -> int:
    sql = f"""
    SELECT COUNT(*) as c FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.training_jobs`
    WHERE dataset_id = @p0 AND status IN ('PENDING', 'RUNNING')
    """
    for row in _bq_query(sql, [dataset_id]):
        return row.c
    return 0


# ── Training Jobs ────────────────────────────────────────────────────────


def create_job(job_id, concern, algorithm="auto", dataset_id=None, filters=None):
    sql = f"""
    INSERT INTO `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.training_jobs`
    (id, concern, algorithm, dataset_id, filters, status, progress, stage, created_at)
    VALUES (@p0, @p1, @p2, @p3, PARSE_JSON(@p4), 'PENDING', 0, 'Queued', CURRENT_TIMESTAMP())
    """
    _bq_query(sql, [job_id, concern, algorithm, dataset_id, json.dumps(filters or {})])


def update_job(job_id: str, **kwargs):
    if not kwargs:
        return
    sets = []
    params = []
    for k, v in kwargs.items():
        if isinstance(v, (dict, list)):
            sets.append(f"{k} = PARSE_JSON(@p{len(params)})")
            params.append(json.dumps(v))
        else:
            sets.append(f"{k} = @p{len(params)}")
            params.append(v)
    params.append(job_id)
    set_sql = ", ".join(sets)
    sql = f"UPDATE `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.training_jobs` SET {set_sql} WHERE id = @p{len(params)-1}"
    _bq_query(sql, params)


def get_job(job_id: str) -> Optional[Dict[str, Any]]:
    sql = f"SELECT * FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.training_jobs` WHERE id = @p0"
    for row in _bq_query(sql, [job_id]):
        return _row_to_dict(row)
    return None


def delete_job(job_id: str) -> bool:
    sql = f"DELETE FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.training_jobs` WHERE id = @p0"
    _bq_query(sql, [job_id])
    return True


def list_jobs(concern=None, status=None, limit=100) -> List[Dict[str, Any]]:
    where_clauses = []
    params = []
    if concern:
        where_clauses.append("concern = @p0")
        params.append(concern)
    if status:
        where_clauses.append(f"status = @p{len(params)}")
        params.append(status)
    where = " AND ".join(where_clauses)
    where_sql = f"WHERE {where}" if where else ""
    sql = f"SELECT * FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.training_jobs` {where_sql} ORDER BY created_at DESC LIMIT {limit}"
    return [_row_to_dict(r) for r in _bq_query(sql, params)]


# ── League Data (from scrimfinder dataset) ───────────────────────────────


def query_league(sql: str, params: Optional[List[Any]] = None):
    """Execute query against the league data dataset."""
    league_tables = ["matches", "player_stats", "team_stats", "bans", "player_items", "player_runes",
                     "dim_champions", "dim_items", "dim_runes", "dim_players"]
    for table in league_tables:
        sql = sql.replace(f"FROM {table}", f"FROM `{cfg.BQ_PROJECT}.{cfg.BQ_DATASET}.{table}`")
        sql = sql.replace(f"JOIN {table}", f"JOIN `{cfg.BQ_PROJECT}.{cfg.BQ_DATASET}.{table}`")
    return _bq_query(sql, params)


def get_league_match(match_id: str) -> Optional[Dict[str, Any]]:
    sql = f"SELECT * FROM `{cfg.BQ_PROJECT}.{cfg.BQ_DATASET}.matches` WHERE match_id = @p0"
    for row in _bq_query(sql, [match_id]):
        return _row_to_dict(row)
    return None


def list_league_matches(limit=100, offset=0) -> List[Dict[str, Any]]:
    sql = f"SELECT * FROM `{cfg.BQ_PROJECT}.{cfg.BQ_DATASET}.matches` ORDER BY timestamp DESC LIMIT {limit} OFFSET {offset}"
    return [_row_to_dict(r) for r in _bq_query(sql)]


def get_player_stats(match_id: str) -> List[Dict[str, Any]]:
    sql = f"SELECT * FROM `{cfg.BQ_PROJECT}.{cfg.BQ_DATASET}.player_stats` WHERE match_id = @p0"
    return [_row_to_dict(r) for r in _bq_query(sql, [match_id])]


def get_team_stats(match_id: str) -> List[Dict[str, Any]]:
    sql = f"SELECT * FROM `{cfg.BQ_PROJECT}.{cfg.BQ_DATASET}.team_stats` WHERE match_id = @p0"
    return [_row_to_dict(r) for r in _bq_query(sql, [match_id])]


def get_bans(match_id: str) -> List[Dict[str, Any]]:
    sql = f"SELECT * FROM `{cfg.BQ_PROJECT}.{cfg.BQ_DATASET}.bans` WHERE match_id = @p0"
    return [_row_to_dict(r) for r in _bq_query(sql, [match_id])]


def get_player_items(match_id: str) -> List[Dict[str, Any]]:
    sql = f"SELECT * FROM `{cfg.BQ_PROJECT}.{cfg.BQ_DATASET}.player_items` WHERE match_id = @p0"
    return [_row_to_dict(r) for r in _bq_query(sql, [match_id])]


def get_player_runes(match_id: str) -> List[Dict[str, Any]]:
    sql = f"SELECT * FROM `{cfg.BQ_PROJECT}.{cfg.BQ_DATASET}.player_runes` WHERE match_id = @p0"
    return [_row_to_dict(r) for r in _bq_query(sql, [match_id])]


def get_dim_champions() -> List[Dict[str, Any]]:
    sql = f"SELECT * FROM `{cfg.BQ_PROJECT}.{cfg.BQ_DATASET}.dim_champions`"
    return [_row_to_dict(r) for r in _bq_query(sql)]


def get_dim_items() -> List[Dict[str, Any]]:
    sql = f"SELECT * FROM `{cfg.BQ_PROJECT}.{cfg.BQ_DATASET}.dim_items`"
    return [_row_to_dict(r) for r in _bq_query(sql)]


def get_dim_runes() -> List[Dict[str, Any]]:
    sql = f"SELECT * FROM `{cfg.BQ_PROJECT}.{cfg.BQ_DATASET}.dim_runes`"
    return [_row_to_dict(r) for r in _bq_query(sql)]


def upsert_league_match(raw: dict) -> None:
    """Persist one normalized league match into BigQuery league tables."""
    match_id = raw.get("match_id") or raw.get("matchId") or raw.get("id")
    if not match_id:
        raise ValueError("League match is missing match_id")

    participants = raw.get("participants") or []
    team_rows = raw.get("teams") or raw.get("team_stats") or []
    bans = raw.get("bans") or []

    # Insert match
    sql = f"""
    MERGE `{cfg.BQ_PROJECT}.{cfg.BQ_DATASET}.matches` T
    USING (SELECT @p0 as match_id, @p1 as patch, @p2 as duration, @p3 as timestamp, @p4 as match_type) S
    ON T.match_id = S.match_id
    WHEN MATCHED THEN UPDATE SET
        patch = S.patch, duration = S.duration, timestamp = S.timestamp, match_type = S.match_type
    WHEN NOT MATCHED THEN INSERT (match_id, patch, duration, timestamp, match_type)
    VALUES (S.match_id, S.patch, S.duration, S.timestamp, S.match_type)
    """
    _bq_query(sql, [
        str(match_id),
        raw.get("patch") or raw.get("gameVersion"),
        raw.get("duration") or raw.get("duration_sec") or raw.get("gameDuration"),
        raw.get("timestamp"),
        raw.get("match_type") or raw.get("gameType") or raw.get("queueType"),
    ])

    # Clear existing child rows
    for table in ["player_items", "player_runes", "bans", "team_stats"]:
        del_sql = f"DELETE FROM `{cfg.BQ_PROJECT}.{cfg.BQ_DATASET}.{table}` WHERE match_id = @p0"
        _bq_query(del_sql, [str(match_id)])

    # Insert participants
    for p in participants:
        puuid = p.get("puuid")
        if not puuid:
            continue

        # Upsert player
        player_sql = f"""
        MERGE `{cfg.BQ_PROJECT}.{cfg.BQ_DATASET}.dim_players` T
        USING (SELECT @p0 as puuid, @p1 as name, @p2 as tag) S
        ON T.puuid = S.puuid
        WHEN MATCHED THEN UPDATE SET
            name = COALESCE(S.name, T.name),
            tag = COALESCE(S.tag, T.tag)
        WHEN NOT MATCHED THEN INSERT (puuid, name, tag)
        VALUES (S.puuid, S.name, S.tag)
        """
        _bq_query(player_sql, [
            str(puuid),
            p.get("name") or p.get("riotIdGameName") or p.get("summonerName"),
            p.get("tag") or p.get("riotIdTagline"),
        ])

        # Insert player_stats
        stats_sql = f"""
        INSERT INTO `{cfg.BQ_PROJECT}.{cfg.BQ_DATASET}.player_stats`
        (match_id, puuid, champion_id, team_id, win, position, kills, deaths, assists,
         gold, cs, dmg_champs, vision, kda, kp, summ1, summ2)
        VALUES (@p0, @p1, @p2, @p3, @p4, @p5, @p6, @p7, @p8, @p9, @p10, @p11, @p12, @p13, @p14, @p15, @p16)
        """
        _bq_query(stats_sql, [
            str(match_id), str(puuid),
            str(p.get("champion_id") or p.get("championId") or ""),
            str(p.get("team_id") or p.get("teamId") or ""),
            int(bool(p.get("win", 0))),
            p.get("position") or p.get("teamPosition"),
            p.get("kills", 0),
            p.get("deaths", 0),
            p.get("assists", 0),
            p.get("gold") or p.get("goldEarned", 0),
            p.get("cs") or p.get("totalMinionsKilled", 0),
            p.get("dmg_champs") or p.get("totalDamageDealtToChampions") or p.get("totalDamageDealt", 0),
            p.get("vision") or p.get("visionScore") or p.get("wardsPlaced", 0),
            p.get("kda", 0.0),
            p.get("kp", 0.0),
            p.get("summ1"),
            p.get("summ2"),
        ])

        # Insert items
        for item in p.get("items") or []:
            item_sql = f"INSERT INTO `{cfg.BQ_PROJECT}.{cfg.BQ_DATASET}.player_items` (match_id, puuid, item_id, slot) VALUES (@p0, @p1, @p2, @p3)"
            _bq_query(item_sql, [
                str(match_id), str(puuid),
                str(item.get("item_id") or item.get("itemId") or ""),
                item.get("slot"),
            ])

        # Insert runes
        for rune in p.get("runes") or []:
            rune_sql = f"INSERT INTO `{cfg.BQ_PROJECT}.{cfg.BQ_DATASET}.player_runes` (match_id, puuid, rune_id) VALUES (@p0, @p1, @p2)"
            _bq_query(rune_sql, [
                str(match_id), str(puuid),
                str(rune.get("rune_id") or rune.get("runeId") or ""),
            ])

    # Insert team stats
    for t in team_rows:
        team_sql = f"""
        INSERT INTO `{cfg.BQ_PROJECT}.{cfg.BQ_DATASET}.team_stats`
        (match_id, team_id, win, baron, dragon, tower, inhibitor, horde, first_blood, first_tower, first_dragon)
        VALUES (@p0, @p1, @p2, @p3, @p4, @p5, @p6, @p7, @p8, @p9, @p10)
        """
        _bq_query(team_sql, [
            str(match_id),
            str(t.get("team_id") or t.get("teamId") or ""),
            t.get("win"),
            t.get("baron"),
            t.get("dragon"),
            t.get("tower"),
            t.get("inhibitor"),
            t.get("horde"),
            t.get("first_blood"),
            t.get("first_tower"),
            t.get("first_dragon"),
        ])

    # Insert bans
    for ban in bans:
        ban_sql = f"INSERT INTO `{cfg.BQ_PROJECT}.{cfg.BQ_DATASET}.bans` (match_id, team_id, champion_id, pick_turn) VALUES (@p0, @p1, @p2, @p3)"
        _bq_query(ban_sql, [
            str(match_id),
            str(ban.get("team_id") or ban.get("teamId") or ""),
            str(ban.get("champion_id") or ban.get("championId") or ""),
            ban.get("pick_turn") or ban.get("pickTurn"),
        ])


def upsert_dimension_rows(table: str, rows: List[Dict[str, Any]]) -> int:
    """Upsert dimension table rows (champions, items, runes, players)."""
    if not rows:
        return 0

    # Get columns from first row
    columns = list(rows[0].keys())
    conflict_col = columns[0]

    # BigQuery MERGE for batch upsert
    for row in rows:
        sets = [f"{col} = S.{col}" for col in columns[1:]]
        vals = [f"@p{i}" for i in range(len(columns))]
        params = [row.get(c) for c in columns]
        sql = f"""
        MERGE `{cfg.BQ_PROJECT}.{cfg.BQ_DATASET}.{table}` T
        USING (SELECT {', '.join(f"@p{i} as {columns[i]}" for i in range(len(columns)))}) S
        ON T.{conflict_col} = S.{conflict_col}
        WHEN MATCHED THEN UPDATE SET {', '.join(sets)}
        WHEN NOT MATCHED THEN INSERT ({', '.join(columns)})
        VALUES ({', '.join(vals)})
        """
        _bq_query(sql, params)

    return len(rows)