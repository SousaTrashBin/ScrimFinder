"""
services/champion/queries.py
All SQL queries for the Champion Analysis Service.
Queries run directly against league_data.db (read-only).
"""

import os
import sqlite3
from typing import Optional

DB_PATH = os.environ.get(
    "LEAGUE_DB",
    os.path.join(os.path.dirname(__file__), "..", "..", "..", "dataset", "league_data.db"),
)


def _connect() -> sqlite3.Connection:
    if not os.path.exists(DB_PATH):
        raise FileNotFoundError(
            f"Database not found at {DB_PATH}. "
            "Set the LEAGUE_DB environment variable to the correct path."
        )
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def _pos_filter(position: Optional[str]) -> tuple[str, list]:
    """Return a SQL fragment and its params for an optional position filter."""
    if position:
        return "AND ps.position = ?", [position]
    return "", []


def _match_type_filter(match_type: Optional[str]) -> tuple[str, list]:
    if match_type:
        return "AND m.match_type = ?", [match_type]
    return "", []


# ── Champion lookup ───────────────────────────────────────────

def get_champion_id(name: str) -> Optional[int]:
    """Return champion_id for a given name, or None if not found."""
    with _connect() as conn:
        row = conn.execute(
            "SELECT id FROM dim_champions WHERE LOWER(name) = LOWER(?)", (name,)
        ).fetchone()
    return row["id"] if row else None


def get_item_names(item_ids: list[int]) -> list[str]:
    if not item_ids:
        return []
    placeholders = ",".join("?" * len(item_ids))
    with _connect() as conn:
        rows = conn.execute(
            f"SELECT id, name FROM dim_items WHERE id IN ({placeholders})", item_ids
        ).fetchall()
    id_to_name = {r["id"]: r["name"] for r in rows}
    return [id_to_name.get(i, str(i)) for i in item_ids]


# ── Win rate ──────────────────────────────────────────────────

def query_winrate(
    champion_id: int,
    position: Optional[str] = None,
    match_type: Optional[str] = None,
) -> dict:
    pos_sql,  pos_p  = _pos_filter(position)
    mt_sql,   mt_p   = _match_type_filter(match_type)

    sql = f"""
        SELECT
            SUM(ps.win)         AS wins,
            SUM(1 - ps.win)     AS losses,
            COUNT(*)            AS total
        FROM player_stats ps
        JOIN matches m ON ps.match_id = m.match_id
        WHERE ps.champion_id = ?
          AND ps.position != 'Invalid'
          {pos_sql}
          {mt_sql}
    """
    with _connect() as conn:
        row = conn.execute(sql, [champion_id] + pos_p + mt_p).fetchone()
    return dict(row) if row else {"wins": 0, "losses": 0, "total": 0}


# ── Positions ─────────────────────────────────────────────────

def query_positions(
    champion_id: int,
    match_type: Optional[str] = None,
) -> list[dict]:
    mt_sql, mt_p = _match_type_filter(match_type)
    sql = f"""
        SELECT
            ps.position,
            COUNT(*)                                    AS total,
            SUM(ps.win)                                 AS wins,
            ROUND(AVG(ps.kda), 2)                       AS avg_kda,
            ROUND(AVG(ps.dmg_champs), 0)                AS avg_damage
        FROM player_stats ps
        JOIN matches m ON ps.match_id = m.match_id
        WHERE ps.champion_id = ?
          AND ps.position != 'Invalid'
          {mt_sql}
        GROUP BY ps.position
        ORDER BY wins * 1.0 / total DESC
    """
    with _connect() as conn:
        rows = conn.execute(sql, [champion_id] + mt_p).fetchall()
    total_games = sum(r["total"] for r in rows)
    result = []
    for r in rows:
        result.append({
            "position":   r["position"],
            "winRate":    round(r["wins"] / r["total"] * 100, 2),
            "pickRate":   round(r["total"] / total_games * 100, 2) if total_games else 0,
            "totalGames": r["total"],
            "avgKda":     r["avg_kda"],
            "avgDamage":  int(r["avg_damage"]),
        })
    return result


# ── Stats ─────────────────────────────────────────────────────

def query_stats(
    champion_id: int,
    position: Optional[str] = None,
    match_type: Optional[str] = None,
) -> Optional[dict]:
    pos_sql, pos_p = _pos_filter(position)
    mt_sql,  mt_p  = _match_type_filter(match_type)
    sql = f"""
        SELECT
            COUNT(*)                    AS total,
            ROUND(AVG(ps.kills),  2)    AS avg_kills,
            ROUND(AVG(ps.deaths), 2)    AS avg_deaths,
            ROUND(AVG(ps.assists),2)    AS avg_assists,
            ROUND(AVG(ps.kda),    2)    AS avg_kda,
            ROUND(AVG(ps.dmg_champs),0) AS avg_damage,
            ROUND(AVG(ps.gold),   0)    AS avg_gold,
            ROUND(AVG(ps.cs),     1)    AS avg_cs,
            ROUND(AVG(ps.vision), 1)    AS avg_vision,
            ROUND(AVG(ps.kp),     3)    AS avg_kp
        FROM player_stats ps
        JOIN matches m ON ps.match_id = m.match_id
        WHERE ps.champion_id = ?
          AND ps.position != 'Invalid'
          {pos_sql}
          {mt_sql}
    """
    with _connect() as conn:
        row = conn.execute(sql, [champion_id] + pos_p + mt_p).fetchone()
    return dict(row) if row and row["total"] else None


# ── Matchups ──────────────────────────────────────────────────

def query_matchups(
    champion_id: int,
    position: Optional[str] = None,
    match_type: Optional[str] = None,
    cross_lane: bool = False,
    limit: int = 20,
    min_samples: int = 50,
) -> list[dict]:
    """
    For each opponent champion, compute win rate of champion_id against them.
    'Opponent' means they were on the opposing team in the same match.
    """
    pos_filter_my   = "AND my.position = ?"    if position and not cross_lane else ""
    pos_filter_opp  = "AND opp.position = ?"   if position and not cross_lane else ""
    mt_sql, mt_p    = _match_type_filter(match_type)
    pos_p           = [position] * (2 if position and not cross_lane else 0)

    sql = f"""
        SELECT
            dc.name                                         AS opponent,
            COUNT(*)                                        AS total,
            SUM(my.win)                                     AS wins,
            ROUND(AVG(my.kda), 2)                           AS avg_kda
        FROM player_stats my
        JOIN player_stats opp
          ON  my.match_id = opp.match_id
          AND my.team_id != opp.team_id
        JOIN dim_champions dc ON opp.champion_id = dc.id
        JOIN matches m ON my.match_id = m.match_id
        WHERE my.champion_id = ?
          AND my.position  != 'Invalid'
          AND opp.position != 'Invalid'
          {pos_filter_my}
          {pos_filter_opp}
          {mt_sql}
        GROUP BY opp.champion_id
        HAVING total >= ?
        ORDER BY wins * 1.0 / total DESC
        LIMIT ?
    """
    params = [champion_id] + pos_p + mt_p + [min_samples, limit]
    with _connect() as conn:
        rows = conn.execute(sql, params).fetchall()

    return [
        {
            "opponent":   r["opponent"],
            "winRate":    round(r["wins"] / r["total"] * 100, 2),
            "totalGames": r["total"],
            "avgKda":     r["avg_kda"],
        }
        for r in rows
    ]


# ── Builds ────────────────────────────────────────────────────

def query_builds(
    champion_id: int,
    position: Optional[str] = None,
    match_type: Optional[str] = None,
    limit: int = 5,
    min_samples: int = 100,
) -> list[dict]:
    """
    Group items purchased together per participant, rank by win rate.
    Items are sorted within each build for consistent grouping.
    """
    pos_sql, pos_p = _pos_filter(position)
    mt_sql,  mt_p  = _match_type_filter(match_type)

    # Aggregate item_ids per participant into a sorted tuple for grouping
    sql = f"""
        SELECT
            ps.match_id,
            ps.puuid,
            ps.win,
            GROUP_CONCAT(pi.item_id ORDER BY pi.item_id) AS item_key,
            COUNT(pi.item_id)                             AS n_items
        FROM player_stats ps
        JOIN player_items pi ON ps.match_id = pi.match_id AND ps.puuid = pi.puuid
        JOIN matches m ON ps.match_id = m.match_id
        WHERE ps.champion_id = ?
          AND ps.position != 'Invalid'
          {pos_sql}
          {mt_sql}
        GROUP BY ps.match_id, ps.puuid
        HAVING n_items >= 3
    """
    params = [champion_id] + pos_p + mt_p
    with _connect() as conn:
        rows = conn.execute(sql, params).fetchall()

    if not rows:
        return []

    # Aggregate by item_key
    from collections import defaultdict
    build_stats: dict[str, dict] = defaultdict(lambda: {"wins": 0, "total": 0})
    total_participants = len(rows)
    for r in rows:
        key = r["item_key"]
        build_stats[key]["wins"]  += r["win"]
        build_stats[key]["total"] += 1

    # Filter by min_samples, sort by win rate
    filtered = [
        (key, s) for key, s in build_stats.items() if s["total"] >= min_samples
    ]
    filtered.sort(key=lambda x: x[1]["wins"] / x[1]["total"], reverse=True)
    filtered = filtered[:limit]

    # Resolve item IDs → names
    result = []
    with _connect() as conn:
        for key, s in filtered:
            item_ids = [int(i) for i in key.split(",")]
            placeholders = ",".join("?" * len(item_ids))
            name_rows = conn.execute(
                f"SELECT id, name FROM dim_items WHERE id IN ({placeholders})", item_ids
            ).fetchall()
            id_to_name = {r["id"]: r["name"] for r in name_rows}
            item_names = [id_to_name.get(i, str(i)) for i in item_ids]
            result.append({
                "items":      item_names,
                "winRate":    round(s["wins"] / s["total"] * 100, 2),
                "pickRate":   round(s["total"] / total_participants * 100, 2),
                "totalGames": s["total"],
            })
    return result


# ── Meta: top champions ────────────────────────────────────────

def query_top_champions(
    position: Optional[str] = None,
    match_type: Optional[str] = None,
    limit: int = 10,
    min_samples: int = 200,
    sort_by: str = "winRate",
) -> list[dict]:
    pos_sql, pos_p = _pos_filter(position)
    mt_sql,  mt_p  = _match_type_filter(match_type)

    sort_col = {
        "winRate":   "win_rate",
        "pickRate":  "pick_rate",
        "avgKda":    "avg_kda",
        "avgDamage": "avg_damage",
    }.get(sort_by, "win_rate")

    # Total games for pick rate denominator
    with _connect() as conn:
        total_row = conn.execute(
            f"""SELECT COUNT(DISTINCT ps.match_id) AS total
                FROM player_stats ps
                JOIN matches m ON ps.match_id = m.match_id
                WHERE ps.position != 'Invalid' {pos_sql} {mt_sql}""",
            pos_p + mt_p,
        ).fetchone()
        total_matches = total_row["total"] or 1

        sql = f"""
            SELECT
                dc.name                                    AS champion,
                COUNT(*)                                   AS total,
                ROUND(SUM(ps.win)*100.0 / COUNT(*), 2)    AS win_rate,
                ROUND(COUNT(*)*100.0 / ?, 2)              AS pick_rate,
                ROUND(AVG(ps.kda), 2)                     AS avg_kda,
                ROUND(AVG(ps.dmg_champs), 0)              AS avg_damage
            FROM player_stats ps
            JOIN dim_champions dc ON ps.champion_id = dc.id
            JOIN matches m ON ps.match_id = m.match_id
            WHERE ps.position != 'Invalid'
              {pos_sql}
              {mt_sql}
            GROUP BY ps.champion_id
            HAVING total >= ?
            ORDER BY {sort_col} DESC
            LIMIT ?
        """
        rows = conn.execute(
            sql, [total_matches] + pos_p + mt_p + [min_samples, limit]
        ).fetchall()

    return [
        {
            "rank":       i + 1,
            "champion":   r["champion"],
            "winRate":    r["win_rate"],
            "pickRate":   r["pick_rate"],
            "avgKda":     r["avg_kda"],
            "avgDamage":  int(r["avg_damage"]),
            "totalGames": r["total"],
        }
        for i, r in enumerate(rows)
    ]