"""
training/data_loader.py
Chunked data loading with live progress and cancellation support.

Pure BigQuery implementation. PostgreSQL/SQLite fallbacks removed.
"""

from typing import Callable, Optional

import numpy as np
import pandas as pd
from sklearn.preprocessing import LabelEncoder, MultiLabelBinarizer

from training_service.core.db import _bq_query
from training_service.core.config import cfg

VALID_POSITIONS = ["TOP", "JUNGLE", "MIDDLE", "BOTTOM", "UTILITY"]

POSITION_MAP = {
    "TOP": "TOP",
    "JUNGLE": "JUNGLE",
    "MIDDLE": "MID",
    "BOTTOM": "BOT",
    "UTILITY": "SUPPORT",
}

# Approx full-dataset row counts for progress estimation (slightly under so bar doesn't stall)
APPROX_ROWS = {
    "player_stats": 4_800_000,
    "player_items": 30_000_000,
    "player_runes": 28_000_000,
    "matches": 490_000,
}

CHUNK_SIZE = 200_000

Report = Callable[[int, str], None]
Filters = dict  # keys: sample (float), limit (int), matchType (str)


def _sql(query: str) -> str:
    tables = [
        "dim_champions",
        "dim_items",
        "dim_runes",
        "dim_players",
        "matches",
        "player_stats",
        "team_stats",
        "bans",
        "player_items",
        "player_runes",
    ]
    import re

    for table in tables:
        # Replace table references with fully-qualified BigQuery names
        query = re.sub(
            rf"\b{table}\b", f"`{cfg.BQ_PROJECT}.{cfg.BQ_DATASET}.{table}`", query
        )
    # BigQuery uses RAND() instead of RANDOM()
    query = query.replace("RANDOM()", "RAND()")
    return query


def _read_bq(
    query: str, params: Optional[list] = None, label: str = ""
) -> pd.DataFrame:
    """
    Execute query via _bq_query and return a DataFrame.
    Uses %s placeholders (converted to @pN by _bq_query).
    """
    sql_query = _sql(query)
    # _bq_query handles %s -> @pN conversion and parameter binding
    rows = list(_bq_query(sql_query, params or []))
    if not rows:
        return pd.DataFrame()
    # Convert BigQuery Row objects to dicts
    data = [dict(row.items()) for row in rows]
    return pd.DataFrame(data)


def _match_id_clause(filters: Filters) -> tuple[str, list]:
    """
    Build a WHERE clause fragment that restricts to a random sample of match_ids.
    Returns (clause_sql, params).
    """
    match_type = filters.get("match_type") or filters.get("matchType")
    sample = filters.get("sample", 1.0)
    limit = filters.get("limit")

    if match_type:
        where = "WHERE match_type = %s"
        params = [match_type]
    else:
        where = ""
        params = []

    # BigQuery uses RAND()
    random_func = "RAND()"

    if sample and sample < 1.0:
        order = f"ORDER BY {random_func}"
        n = int(APPROX_ROWS["matches"] * sample)
        lim = f"LIMIT {n}"
    elif limit:
        n = max(1, limit // 10)
        order = f"ORDER BY {random_func}"
        lim = f"LIMIT {n}"
    else:
        order = ""
        lim = ""

    if order or where:
        subq = f"SELECT match_id FROM matches {where} {order} {lim}"
        clause = f"AND ps.match_id IN ({subq})"
    else:
        clause = ""

    return clause, params


# ══════════════════════════════════════════════════════════════
# DRAFT
# ══════════════════════════════════════════════════════════════


def load_draft_data(report: Report, filters: Optional[Filters] = None):
    filters = filters or {}
    report(0, "Connecting to database…")

    match_clause, params = _match_id_clause(filters)

    query = f"""
        SELECT ps.match_id, ps.champion_id, ps.team_id, ps.win
        FROM player_stats ps
        WHERE ps.position != 'Invalid'
        {match_clause}
    """

    n_matches = APPROX_ROWS["matches"]
    sample = filters.get("sample", 1.0)
    limit = filters.get("limit")
    approx = int(n_matches * (sample or 1.0)) * 10
    if limit:
        approx = min(approx, limit)

    report(2, "Loading draft data…")
    df = _read_bq(query, params, "Loading draft data")
    report(38, f"Loaded {len(df):,} rows")

    if df.empty:
        raise ValueError("No data returned from BigQuery — check filters.")

    df["team_id"] = df["team_id"].astype(str)

    report(38, f"Pivoting {len(df):,} rows into team compositions…")

    blue = (
        df[df["team_id"] == "100"]
        .groupby("match_id")["champion_id"]
        .apply(list)
        .reset_index()
        .rename(columns={"champion_id": "blue_champs"})
    )

    red = (
        df[df["team_id"] == "200"]
        .groupby("match_id")["champion_id"]
        .apply(list)
        .reset_index()
        .rename(columns={"champion_id": "red_champs"})
    )

    wins = (
        df[df["team_id"] == "100"][["match_id", "win"]]
        .groupby("match_id")["win"]
        .first()
        .reset_index()
    )

    report(52, "Merging teams…")
    merged = blue.merge(red, on="match_id").merge(wins, on="match_id")

    # Keep only complete 5v5 matches
    merged = merged[
        merged["blue_champs"].apply(len).eq(5) & merged["red_champs"].apply(len).eq(5)
    ]

    if len(merged) == 0:
        raise ValueError(
            f"All matches were filtered out after requiring 5v5 completeness. "
            f"Raw rows loaded: {len(df):,}. "
        )

    report(60, f"Encoding {len(merged):,} complete matches…")
    mlb = MultiLabelBinarizer()
    mlb.fit(merged["blue_champs"].tolist() + merged["red_champs"].tolist())

    report(72, "Building feature matrix…")
    blue_enc = mlb.transform(merged["blue_champs"])
    red_enc = mlb.transform(merged["red_champs"])
    X = np.hstack([blue_enc, red_enc]).astype(np.float32)
    y = merged["win"].values.astype(np.int32)

    report(95, f"Data ready — {len(merged):,} matches, {X.shape[1]} features")
    return X, y, mlb


# ══════════════════════════════════════════════════════════════
# BUILD
# ══════════════════════════════════════════════════════════════


def load_build_data(report: Report, filters: Optional[Filters] = None):
    filters = filters or {}
    report(0, "Connecting to database…")

    match_clause, params = _match_id_clause(filters)

    sample = filters.get("sample", 1.0)
    limit = filters.get("limit")
    approx_ps = int(APPROX_ROWS["player_stats"] * (sample or 1.0))
    if limit:
        approx_ps = min(approx_ps, limit)

    report(2, "Loading player stats…")
    stats = _read_bq(
        f"""
            SELECT match_id, puuid, champion_id, position, win, gold, cs, dmg_champs
            FROM player_stats
            WHERE position != 'Invalid'
            {match_clause.replace("ps.match_id", "match_id") if match_clause else ""}
        """,
        params,
        "Loading player stats",
    )
    if stats.empty:
        raise ValueError("No player stats returned from BigQuery.")
    puuids = stats[["match_id", "puuid"]].drop_duplicates()
    report(20, f"Loading items for {len(puuids):,} players…")

    items = _read_bq(
        f"""
            SELECT match_id, puuid, item_id FROM player_items
            {
            (
                "WHERE "
                + match_clause.replace("ps.match_id", "match_id").replace("AND ", "", 1)
            )
            if match_clause
            else ""
        }
        """,
        params,
        "Loading items",
    )
    report(45, "Loading runes…")

    runes = _read_bq(
        f"""
            SELECT match_id, puuid, rune_id FROM player_runes
            {
            (
                "WHERE "
                + match_clause.replace("ps.match_id", "match_id").replace("AND ", "", 1)
            )
            if match_clause
            else ""
        }
        """,
        params,
        "Loading runes",
    )
    report(62, "Grouping items per player…")
    if items.empty:
        items_g = pd.DataFrame(columns=["match_id", "puuid", "item_ids"])
    else:
        items_g = (
            items.groupby(["match_id", "puuid"])["item_id"]
            .apply(list)
            .reset_index()
            .rename(columns={"item_id": "item_ids"})
        )

    report(66, "Grouping runes per player…")
    if runes.empty:
        runes_g = pd.DataFrame(columns=["match_id", "puuid", "rune_ids"])
    else:
        runes_g = (
            runes.groupby(["match_id", "puuid"])["rune_id"]
            .apply(list)
            .reset_index()
            .rename(columns={"rune_id": "rune_ids"})
        )

    report(69, "Joining features…")
    df = stats.merge(items_g, on=["match_id", "puuid"], how="left").merge(
        runes_g, on=["match_id", "puuid"], how="left"
    )
    df["item_ids"] = df["item_ids"].apply(lambda x: x if isinstance(x, list) else [])
    df["rune_ids"] = df["rune_ids"].apply(lambda x: x if isinstance(x, list) else [])

    report(73, "Encoding features…")
    item_mlb = MultiLabelBinarizer()
    item_enc = item_mlb.fit_transform(df["item_ids"])
    rune_mlb = MultiLabelBinarizer()
    rune_enc = rune_mlb.fit_transform(df["rune_ids"])
    pos_le = LabelEncoder()
    pos_enc = pos_le.fit_transform(df["position"]).reshape(-1, 1)
    champ_le = LabelEncoder()
    champ_enc = champ_le.fit_transform(df["champion_id"]).reshape(-1, 1)

    report(88, "Assembling feature matrix…")
    numeric = df[["gold", "cs", "dmg_champs"]].fillna(0).values
    X = np.hstack([item_enc, rune_enc, pos_enc, champ_enc, numeric]).astype(np.float32)
    y = df["win"].values.astype(np.int32)

    encoders = {
        "item_mlb": item_mlb,
        "rune_mlb": rune_mlb,
        "pos_le": pos_le,
        "champ_le": champ_le,
    }

    report(95, f"Data ready — shape {X.shape}")
    return X, y, encoders


# ══════════════════════════════════════════════════════════════
# PERFORMANCE
# ══════════════════════════════════════════════════════════════


def load_performance_data(report: Report, filters: Optional[Filters] = None):
    filters = filters or {}
    report(0, "Connecting to database…")

    match_clause, params = _match_id_clause(filters)
    mc = match_clause.replace("ps.match_id", "match_id") if match_clause else ""

    sample = filters.get("sample", 1.0)
    int(APPROX_ROWS["player_stats"] * (sample or 1.0))

    report(2, "Loading performance data…")
    df = _read_bq(
        f"""
        SELECT match_id, puuid, champion_id, position, win,
               kills, deaths, assists, gold, cs,
               dmg_champs, vision, kda, kp
        FROM player_stats
        WHERE position != 'Invalid'
        {mc}
    """,
        params,
        "Loading performance data",
    )

    if df.empty:
        raise ValueError("No data returned from BigQuery.")

    percentile_table = {}
    PERF_METRICS = [
        "kills",
        "deaths",
        "assists",
        "gold",
        "cs",
        "dmg_champs",
        "vision",
        "kda",
        "kp",
    ]
    for i, pos in enumerate(VALID_POSITIONS):
        pct = 50 + int((i / len(VALID_POSITIONS)) * 18)
        report(pct, f"Computing percentiles for {pos}…")
        subset = df[df["position"] == pos]
        if len(subset) == 0:
            continue
        mapped = POSITION_MAP[pos]
        percentile_table[mapped] = {}
        for metric in PERF_METRICS:
            vals = subset[metric].dropna()
            if len(vals) == 0:
                continue
            percentile_table[mapped][metric] = {
                "p10": round(float(np.percentile(vals, 10)), 3),
                "p25": round(float(np.percentile(vals, 25)), 3),
                "p50": round(float(np.percentile(vals, 50)), 3),
                "p75": round(float(np.percentile(vals, 75)), 3),
                "p90": round(float(np.percentile(vals, 90)), 3),
            }

    report(70, "Encoding position + champion…")
    pos_le = LabelEncoder()
    pos_enc = pos_le.fit_transform(df["position"]).reshape(-1, 1)
    champ_le = LabelEncoder()
    champ_enc = champ_le.fit_transform(df["champion_id"]).reshape(-1, 1)

    report(78, "Assembling feature matrix…")
    numeric_cols = [
        "kills",
        "deaths",
        "assists",
        "gold",
        "cs",
        "dmg_champs",
        "vision",
        "kda",
        "kp",
    ]
    numeric = df[numeric_cols].fillna(0).values
    X = np.hstack([pos_enc, champ_enc, numeric]).astype(np.float32)
    y = df["win"].values.astype(np.int32)

    encoders = {"pos_le": pos_le, "champ_le": champ_le}
    report(95, f"Data ready — shape {X.shape}")
    return X, y, encoders, percentile_table
