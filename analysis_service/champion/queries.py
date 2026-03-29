"""
analysis_service/champion/queries.py
All SQL queries against the EUW league_data.db dataset.

DB Schema:
  matches:      match_id, patch, duration, timestamp, match_type
  player_stats: match_id, puuid, champion_id, team_id, win, position,
                kills, deaths, assists, gold, cs, dmg_champs, vision, kda, kp, summ1, summ2
  dim_players:  puuid, name, tag
  dim_champions: id, name
  dim_items:    id, name
  player_items: match_id, puuid, item_id, slot
  player_runes: match_id, puuid, rune_id
"""

import os
import sqlite3
from typing import Optional

from analysis_service.core.config import cfg


def _connect() -> sqlite3.Connection:
    if not os.path.exists(cfg.LEAGUE_DB):
        raise FileNotFoundError(f"EUW database not found at '{cfg.LEAGUE_DB}'. Set the LEAGUE_DB environment variable.")
    conn = sqlite3.connect(cfg.LEAGUE_DB)
    conn.row_factory = sqlite3.Row
    return conn


# ── Champion lookups ──────────────────────────────────────────


def get_champion_id(name: str) -> int | None:
    with _connect() as conn:
        row = conn.execute("SELECT id FROM dim_champions WHERE LOWER(name) = LOWER(?)", (name,)).fetchone()
    return row["id"] if row else None


def get_champion_name_by_id(champion_id: int) -> str | None:
    with _connect() as conn:
        row = conn.execute("SELECT name FROM dim_champions WHERE id = ?", (champion_id,)).fetchone()
    return row["name"] if row else None


def get_champion_name(champion_id: int) -> str | None:
    return get_champion_name_by_id(champion_id)


# ── Item lookups ──────────────────────────────────────────────


def get_item_names(item_ids: list) -> list[str]:
    if not item_ids:
        return []
    placeholders = ",".join("?" * len(item_ids))
    with _connect() as conn:
        rows = conn.execute(f"SELECT id, name FROM dim_items WHERE id IN ({placeholders})", item_ids).fetchall()
    id_to_name = {r["id"]: r["name"] for r in rows}
    return [id_to_name.get(i, str(i)) for i in item_ids]


def get_item_ids(item_names: list) -> list[int]:
    if not item_names:
        return []
    placeholders = ",".join("?" * len(item_names))
    with _connect() as conn:
        rows = conn.execute(f"SELECT id, name FROM dim_items WHERE name IN ({placeholders})", item_names).fetchall()
    name_to_id = {r["name"]: r["id"] for r in rows}
    return [name_to_id[n] for n in item_names if n in name_to_id]


# ── Win rate ──────────────────────────────────────────────────


def query_winrate(champion_id: int, position: str | None = None, match_type: str | None = None) -> dict:
    clauses = ["ps.champion_id = ?"]
    params = [champion_id]
    if position:
        clauses.append("ps.position = ?")
        params.append(position)
    if match_type:
        clauses.append("m.match_type = ?")
        params.append(match_type)
    sql = f"""
        SELECT SUM(ps.win) AS wins, SUM(1-ps.win) AS losses, COUNT(*) AS total
        FROM player_stats ps
        JOIN matches m ON ps.match_id = m.match_id
        WHERE {" AND ".join(clauses)}
    """
    with _connect() as conn:
        row = conn.execute(sql, params).fetchone()
    return dict(row) if row else {"wins": 0, "losses": 0, "total": 0}


# ── Average stats ─────────────────────────────────────────────


def query_stats(champion_id: int, position: str | None = None, match_type: str | None = None) -> dict:
    clauses = ["ps.champion_id = ?"]
    params = [champion_id]
    if position:
        clauses.append("ps.position = ?")
        params.append(position)
    if match_type:
        clauses.append("m.match_type = ?")
        params.append(match_type)
    sql = f"""
        SELECT
            ROUND(AVG(ps.kda),        2) AS avgKda,
            ROUND(AVG(ps.dmg_champs), 0) AS avgDamage,
            ROUND(AVG(ps.gold),       0) AS avgGold,
            ROUND(AVG(ps.cs),         1) AS avgCs,
            ROUND(AVG(ps.vision),     1) AS avgVisionScore
        FROM player_stats ps
        JOIN matches m ON ps.match_id = m.match_id
        WHERE {" AND ".join(clauses)}
    """
    with _connect() as conn:
        row = conn.execute(sql, params).fetchone()
    if row is None:
        return {}
    return {k: round(float(v), 2) if v is not None else 0.0 for k, v in dict(row).items()}


# ── Top items ─────────────────────────────────────────────────


def query_top_items(
    champion_id: int, position: str | None = None, match_type: str | None = None, limit: int = 6
) -> list[str]:
    clauses = [
        "ps.champion_id = ?",
        "ps.win = 1",
        "pi.item_id != 0",
        # Exclude wards, potions, trinkets
        "pi.item_id NOT IN (1001,2003,2031,2055,3340,3364,3363)",
    ]
    params = [champion_id]
    if position:
        clauses.append("ps.position = ?")
        params.append(position)
    if match_type:
        clauses.append("m.match_type = ?")
        params.append(match_type)
    params.append(limit)
    sql = f"""
        SELECT di.name, COUNT(*) AS cnt
        FROM player_items pi
        JOIN player_stats ps ON pi.match_id = ps.match_id AND pi.puuid = ps.puuid
        JOIN matches m       ON ps.match_id = m.match_id
        JOIN dim_items di    ON pi.item_id  = di.id
        WHERE {" AND ".join(clauses)}
        GROUP BY pi.item_id
        ORDER BY cnt DESC
        LIMIT ?
    """
    with _connect() as conn:
        rows = conn.execute(sql, params).fetchall()
    return [r["name"] for r in rows]


# ── Counter matchups ──────────────────────────────────────────


def query_counters(champion_id: int, position: str | None = None, limit: int = 5) -> tuple[list[str], list[str]]:
    """
    Head-to-head win rates. Fast approach:
    1. Get a sample of match_ids where this champion played
    2. Find enemies in those matches only
    """
    pos_clause = "AND ps1.position = ?" if position else ""
    params_step1 = [champion_id]
    if position:
        params_step1.append(position)
    params_step1.append(5000)  # sample 5000 matches max

    sql = f"""
        WITH my_matches AS (
            SELECT match_id, team_id, win
            FROM player_stats
            WHERE champion_id = ? {pos_clause}
            LIMIT ?
        ),
        matchups AS (
            SELECT
                ps.champion_id  AS enemy_id,
                AVG(mm.win)     AS win_rate,
                COUNT(*)        AS games
            FROM my_matches mm
            JOIN player_stats ps
                ON  ps.match_id = mm.match_id
                AND ps.team_id != mm.team_id
            GROUP BY ps.champion_id
            HAVING games > 10
        )
        SELECT dc.name, ROUND(win_rate * 100, 1) AS win_rate, games
        FROM matchups
        JOIN dim_champions dc ON matchups.enemy_id = dc.id
        ORDER BY win_rate DESC
    """
    try:
        with _connect() as conn:
            rows = list(conn.execute(sql, params_step1).fetchall())
        counters = [f"{r['name']} ({r['win_rate']}% WR)" for r in rows[:limit]]
        countered_by = [f"{r['name']} ({r['win_rate']}% WR)" for r in rows[-limit:]]
        return counters, countered_by
    except Exception:
        return [], []


# ── Top champions ─────────────────────────────────────────────


def query_top_champions(position: str | None = None, match_type: str | None = None, limit: int = 10) -> list[dict]:
    clauses, params = [], []
    if position:
        clauses.append("ps.position = ?")
        params.append(position)
    if match_type:
        clauses.append("m.match_type = ?")
        params.append(match_type)
    where = ("WHERE " + " AND ".join(clauses)) if clauses else ""
    params.append(limit)
    sql = f"""
        SELECT dc.name, COUNT(*) AS total, SUM(ps.win) AS wins,
               ROUND(AVG(ps.win)*100, 2) AS win_rate
        FROM player_stats ps
        JOIN matches m        ON ps.match_id    = m.match_id
        JOIN dim_champions dc ON ps.champion_id = dc.id
        {where}
        GROUP BY ps.champion_id
        HAVING total > 100
        ORDER BY win_rate DESC
        LIMIT ?
    """
    with _connect() as conn:
        rows = conn.execute(sql, params).fetchall()
    return [dict(r) for r in rows]


# ── Player stats ──────────────────────────────────────────────


def query_player_stats(
    summoner_id: str,
    last_n: int = 20,
    champion: str | None = None,
    role: str | None = None,
    match_type: str | None = None,
) -> list[dict]:
    _ROLE_TO_DB = {
        "TOP": "TOP",
        "JUNGLE": "JUNGLE",
        "MID": "MIDDLE",
        "BOT": "BOTTOM",
        "SUPPORT": "UTILITY",
    }

    # Step 1: resolve puuid first — dim_players is small, this is fast
    with _connect() as conn:
        if "#" in summoner_id:
            name, tag = summoner_id.split("#", 1)
            row = conn.execute("SELECT puuid FROM dim_players WHERE name = ? AND tag = ?", (name, tag)).fetchone()
        else:
            row = conn.execute(
                "SELECT puuid FROM dim_players WHERE puuid = ? OR name = ? LIMIT 1", (summoner_id, summoner_id)
            ).fetchone()

        if row is None:
            return []
        puuid = row["puuid"]

        # Step 2: query player_stats by puuid directly
        clauses = ["ps.puuid = ?"]
        params = [puuid]

        if champion:
            cid = get_champion_id(champion)
            if cid:
                clauses.append("ps.champion_id = ?")
                params.append(cid)

        if role:
            clauses.append("ps.position = ?")
            params.append(_ROLE_TO_DB.get(role, role))

        if match_type:
            clauses.append("m.match_type = ?")
            params.append(match_type)

        params.append(last_n)
        sql = f"""
            SELECT
                ps.match_id, ps.champion_id, ps.position, ps.win,
                ps.kills, ps.deaths, ps.assists, ps.gold, ps.cs,
                ps.dmg_champs, ps.vision, ps.kda, ps.kp,
                m.duration AS duration_sec
            FROM player_stats ps
            JOIN matches m ON ps.match_id = m.match_id
            WHERE {" AND ".join(clauses)}
            ORDER BY m.timestamp DESC
            LIMIT ?
        """
        rows = conn.execute(sql, params).fetchall()
    return [dict(r) for r in rows]
