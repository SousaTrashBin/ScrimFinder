"""
training/data_loader.py
-----------------------
Chunked data loading and feature engineering for all three models.
Each function accepts a progress callback so the training job can
report live progress through the API.

Callback signature:  report(progress: int, stage: str)
"""

import os
import sqlite3
import numpy as np
import pandas as pd
from typing import Callable
from sklearn.preprocessing import MultiLabelBinarizer, LabelEncoder

# ── Config ────────────────────────────────────────────────────

DB_PATH = os.environ.get(
    "LEAGUE_DB",
    os.path.join(os.path.dirname(__file__), "..", "..", "dataset", "league_data.db"),
)

VALID_POSITIONS = ["TOP", "JUNGLE", "MIDDLE", "BOTTOM", "UTILITY"]

POSITION_MAP = {
    "TOP":     "TOP",
    "JUNGLE":  "JUNGLE",
    "MIDDLE":  "MID",
    "BOTTOM":  "BOT",
    "UTILITY": "SUPPORT",
}

# Chunk size for reading large tables — keeps memory under control
CHUNK_SIZE = 100_000

Report = Callable[[int, str], None]


def _connect() -> sqlite3.Connection:
    if not os.path.exists(DB_PATH):
        raise FileNotFoundError(
            f"Database not found at {DB_PATH}. "
            "Set the LEAGUE_DB environment variable to the correct path."
        )
    return sqlite3.connect(DB_PATH)


def _read_chunked(query: str, label: str, report: Report,
                  progress_start: int, progress_end: int) -> pd.DataFrame:
    """
    Read a SQL query in CHUNK_SIZE chunks, reporting progress between
    progress_start and progress_end.
    """
    conn = _connect()

    # Get total row count for this query so we can show % loaded
    count_query = f"SELECT COUNT(*) FROM ({query})"
    total = pd.read_sql(count_query, conn).iloc[0, 0]

    chunks = []
    loaded = 0
    for chunk in pd.read_sql(query, conn, chunksize=CHUNK_SIZE):
        chunks.append(chunk)
        loaded += len(chunk)
        pct = progress_start + int((loaded / total) * (progress_end - progress_start))
        report(pct, f"{label}: {loaded:,} / {total:,} rows")

    conn.close()
    return pd.concat(chunks, ignore_index=True)


# ══════════════════════════════════════════════════════════════
# DRAFT
# ══════════════════════════════════════════════════════════════

def load_draft_data(report: Report) -> tuple[np.ndarray, np.ndarray, MultiLabelBinarizer]:

    report(0, "Loading match + team data")

    df = _read_chunked("""
        SELECT ps.match_id,
               ps.champion_id,
               ps.team_id,
               ts.win
        FROM player_stats ps
        JOIN team_stats ts
          ON ps.match_id = ts.match_id
         AND ps.team_id  = ts.team_id
        WHERE ps.position != 'Invalid'
    """, "Loading draft rows", report, 0, 30)

    report(30, f"Loaded {len(df):,} rows — pivoting teams")

    blue = (
        df[df["team_id"] == 100]
        .groupby("match_id")["champion_id"]
        .apply(list).reset_index()
        .rename(columns={"champion_id": "blue_champs"})
    )
    red = (
        df[df["team_id"] == 200]
        .groupby("match_id")["champion_id"]
        .apply(list).reset_index()
        .rename(columns={"champion_id": "red_champs"})
    )
    wins = (
        df[df["team_id"] == 100][["match_id", "win"]]
        .drop_duplicates("match_id")
    )

    report(45, "Merging teams")
    merged = blue.merge(red, on="match_id").merge(wins, on="match_id")
    merged = merged[
        merged["blue_champs"].apply(len).eq(5) &
        merged["red_champs"].apply(len).eq(5)
    ]

    report(55, f"Encoding champions ({len(merged):,} complete matches)")
    mlb = MultiLabelBinarizer()
    mlb.fit(merged["blue_champs"].tolist() + merged["red_champs"].tolist())

    report(65, "Building blue team features")
    blue_enc = mlb.transform(merged["blue_champs"])

    report(75, "Building red team features")
    red_enc = mlb.transform(merged["red_champs"])

    report(88, "Assembling feature matrix")
    X = np.hstack([blue_enc, red_enc]).astype(np.float32)
    y = merged["win"].values.astype(np.int32)

    report(95, f"Done — shape {X.shape}, classes {np.bincount(y).tolist()}")
    return X, y, mlb


# ══════════════════════════════════════════════════════════════
# BUILD
# ══════════════════════════════════════════════════════════════

def load_build_data(report: Report) -> tuple[np.ndarray, np.ndarray, dict]:

    report(0, "Loading player stats")
    stats = _read_chunked("""
        SELECT match_id, puuid, champion_id, position, win, gold, cs, dmg_champs
        FROM player_stats
        WHERE position != 'Invalid'
    """, "Loading stats", report, 0, 20)

    report(20, "Loading player items")
    items = _read_chunked("""
        SELECT match_id, puuid, item_id
        FROM player_items
    """, "Loading items", report, 20, 45)

    report(45, "Loading player runes")
    runes = _read_chunked("""
        SELECT match_id, puuid, rune_id
        FROM player_runes
    """, "Loading runes", report, 45, 60)

    report(60, "Grouping items per player")
    items_grouped = (
        items.groupby(["match_id", "puuid"])["item_id"]
        .apply(list).reset_index()
        .rename(columns={"item_id": "item_ids"})
    )

    report(65, "Grouping runes per player")
    runes_grouped = (
        runes.groupby(["match_id", "puuid"])["rune_id"]
        .apply(list).reset_index()
        .rename(columns={"rune_id": "rune_ids"})
    )

    report(68, "Joining stats + items + runes")
    df = (
        stats
        .merge(items_grouped, on=["match_id", "puuid"], how="left")
        .merge(runes_grouped, on=["match_id", "puuid"], how="left")
    )
    df["item_ids"] = df["item_ids"].apply(lambda x: x if isinstance(x, list) else [])
    df["rune_ids"] = df["rune_ids"].apply(lambda x: x if isinstance(x, list) else [])

    report(72, "Encoding items (multi-hot)")
    item_mlb = MultiLabelBinarizer()
    item_enc = item_mlb.fit_transform(df["item_ids"])

    report(80, "Encoding runes (multi-hot)")
    rune_mlb = MultiLabelBinarizer()
    rune_enc = rune_mlb.fit_transform(df["rune_ids"])

    report(85, "Encoding position + champion")
    pos_le   = LabelEncoder()
    pos_enc  = pos_le.fit_transform(df["position"]).reshape(-1, 1)
    champ_le  = LabelEncoder()
    champ_enc = champ_le.fit_transform(df["champion_id"]).reshape(-1, 1)

    report(90, "Assembling feature matrix")
    numeric = df[["gold", "cs", "dmg_champs"]].fillna(0).values
    X = np.hstack([item_enc, rune_enc, pos_enc, champ_enc, numeric]).astype(np.float32)
    y = df["win"].values.astype(np.int32)

    encoders = {
        "item_mlb":  item_mlb,
        "rune_mlb":  rune_mlb,
        "pos_le":    pos_le,
        "champ_le":  champ_le,
    }

    report(95, f"Done — shape {X.shape}, classes {np.bincount(y).tolist()}")
    return X, y, encoders


# ══════════════════════════════════════════════════════════════
# PERFORMANCE
# ══════════════════════════════════════════════════════════════

def load_performance_data(report: Report) -> tuple[np.ndarray, np.ndarray, dict, dict]:

    report(0, "Loading player + team stats")
    df = _read_chunked("""
        SELECT
            ps.match_id, ps.puuid, ps.champion_id, ps.position, ps.win,
            ps.kills, ps.deaths, ps.assists, ps.gold, ps.cs,
            ps.dmg_champs, ps.vision, ps.kda, ps.kp,
            ts.baron, ts.dragon, ts.tower,
            ts.first_blood, ts.first_tower, ts.first_dragon
        FROM player_stats ps
        JOIN team_stats ts
          ON ps.match_id = ts.match_id
         AND ps.team_id  = ts.team_id
        WHERE ps.position != 'Invalid'
    """, "Loading performance rows", report, 0, 40)

    report(40, f"Loaded {len(df):,} rows — computing percentile table")

    PERF_METRICS = ["kills", "deaths", "assists", "gold", "cs",
                    "dmg_champs", "vision", "kda", "kp"]
    percentile_table = {}
    for i, pos in enumerate(VALID_POSITIONS):
        pct = 40 + int((i / len(VALID_POSITIONS)) * 20)
        report(pct, f"Computing percentiles for {pos}")
        subset = df[df["position"] == pos]
        if len(subset) == 0:
            continue
        percentile_table[POSITION_MAP[pos]] = {}
        for metric in PERF_METRICS:
            vals = subset[metric].dropna()
            percentile_table[POSITION_MAP[pos]][metric] = {
                "p10": round(float(np.percentile(vals, 10)), 3),
                "p25": round(float(np.percentile(vals, 25)), 3),
                "p50": round(float(np.percentile(vals, 50)), 3),
                "p75": round(float(np.percentile(vals, 75)), 3),
                "p90": round(float(np.percentile(vals, 90)), 3),
            }

    report(62, "Encoding position + champion")
    pos_le    = LabelEncoder()
    pos_enc   = pos_le.fit_transform(df["position"]).reshape(-1, 1)
    champ_le  = LabelEncoder()
    champ_enc = champ_le.fit_transform(df["champion_id"]).reshape(-1, 1)

    report(72, "Assembling feature matrix")
    numeric_cols = [
        "kills", "deaths", "assists", "gold", "cs",
        "dmg_champs", "vision", "kda", "kp",
        "baron", "dragon", "tower", "first_blood", "first_tower", "first_dragon",
    ]
    numeric = df[numeric_cols].fillna(0).values
    X = np.hstack([pos_enc, champ_enc, numeric]).astype(np.float32)
    y = df["win"].values.astype(np.int32)

    encoders = {"pos_le": pos_le, "champ_le": champ_le}

    report(95, f"Done — shape {X.shape}, classes {np.bincount(y).tolist()}")
    return X, y, encoders, percentile_table