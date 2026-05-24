"""
SQL queries for the league dataset using BigQuery.
"""

from typing import Any, List, Dict, Optional, Tuple
from analysis_service.core.db import query_league, _row_to_dict


def _one(query: str, params: List[Any] = []) -> Optional[Dict[str, Any]]:
    rows = list(query_league(query, params))
    if not rows:
        return None
    return _row_to_dict(rows[0])


def _all(query: str, params: List[Any] = []) -> List[Dict[str, Any]]:
    return [_row_to_dict(r) for r in query_league(query, params)]


def _placeholders(values: list[Any]) -> str:
    return ",".join("%s" for _ in values)


# ── Champion lookups ──────────────────────────────────────────


def get_champion_id(name: str) -> Optional[str]:
    row = _one("SELECT id FROM dim_champions WHERE LOWER(name) = LOWER(%s)", [name])
    return row["id"] if row else None


def get_champion_name_by_id(champion_id: Any) -> Optional[str]:
    row = _one("SELECT name FROM dim_champions WHERE id = %s", [str(champion_id)])
    return row["name"] if row else None


def get_champion_name(champion_id: Any) -> Optional[str]:
    return get_champion_name_by_id(champion_id)


# ── Item lookups ──────────────────────────────────────────────


def get_item_names(item_ids: list) -> List[str]:
    if not item_ids:
        return []
    ids = [str(i) for i in item_ids]
    rows = _all(f"SELECT id, name FROM dim_items WHERE id IN ({_placeholders(ids)})", ids)
    id_to_name = {str(r["id"]): r["name"] for r in rows}
    return [id_to_name.get(str(i), str(i)) for i in item_ids]


def get_item_ids(item_names: list) -> List[str]:
    if not item_names:
        return []
    rows = _all(f"SELECT id, name FROM dim_items WHERE name IN ({_placeholders(item_names)})", item_names)
    name_to_id = {r["name"]: str(r["id"]) for r in rows}
    return [name_to_id[n] for n in item_names if n in name_to_id]


# ── Win rate ──────────────────────────────────────────────────


def query_winrate(champion_id: Any, position: Optional[str] = None, match_type: Optional[str] = None) -> Dict[str, Any]:
    clauses = ["ps.champion_id = %s"]
    params: List[Any] = [str(champion_id)]
    if position:
        clauses.append("ps.position = %s")
        params.append(position)
    if match_type:
        clauses.append("m.match_type = %s")
        params.append(match_type)
    sql = f"""
        SELECT SUM(ps.win) AS wins, SUM(1-ps.win) AS losses, COUNT(*) AS total
        FROM player_stats ps
        JOIN matches m ON ps.match_id = m.match_id
        WHERE {" AND ".join(clauses)}
    """
    return _one(sql, params) or {"wins": 0, "losses": 0, "total": 0}


# ── Average stats ─────────────────────────────────────────────


def query_stats(champion_id: Any, position: Optional[str] = None, match_type: Optional[str] = None) -> Dict[str, Any]:
    clauses = ["ps.champion_id = %s"]
    params: List[Any] = [str(champion_id)]
    if position:
        clauses.append("ps.position = %s")
        params.append(position)
    if match_type:
        clauses.append("m.match_type = %s")
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
    row = _one(sql, params)
    if row is None:
        return {}
    return {k: round(float(v), 2) if v is not None else 0.0 for k, v in row.items()}


# ── Top items ─────────────────────────────────────────────────


def query_top_items(
    champion_id: Any, position: Optional[str] = None, match_type: Optional[str] = None, limit: int = 6
) -> List[str]:
    clauses = [
        "ps.champion_id = %s",
        "ps.win = 1",
        "pi.item_id != '0'",
        "pi.item_id NOT IN ('1001','2003','2031','2055','3340','3364','3363')",
    ]
    params: List[Any] = [str(champion_id)]
    if position:
        clauses.append("ps.position = %s")
        params.append(position)
    if match_type:
        clauses.append("m.match_type = %s")
        params.append(match_type)
    
    sql = f"""
        SELECT di.name, COUNT(*) AS cnt
        FROM player_items pi
        JOIN player_stats ps ON pi.match_id = ps.match_id AND pi.puuid = ps.puuid
        JOIN matches m       ON ps.match_id = m.match_id
        JOIN dim_items di    ON pi.item_id  = di.id
        WHERE {" AND ".join(clauses)}
        GROUP BY pi.item_id, di.name
        ORDER BY cnt DESC
        LIMIT {int(limit)}
    """
    return [r["name"] for r in _all(sql, params)]


# ── Counter matchups ──────────────────────────────────────────


def query_counters(champion_id: Any, position: Optional[str] = None, limit: int = 5) -> Tuple[List[str], List[str]]:
    pos_clause = "AND ps1.position = %s" if position else ""
    params_step1: List[Any] = [str(champion_id)]
    if position:
        params_step1.append(position)

    sql = f"""
        WITH my_matches AS (
            SELECT match_id, team_id, win
            FROM player_stats ps1
            WHERE champion_id = %s {pos_clause}
            LIMIT 5000
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
            HAVING COUNT(*) > 10
        )
        SELECT dc.name, ROUND(win_rate * 100, 1) AS win_rate, games
        FROM matchups
        JOIN dim_champions dc ON matchups.enemy_id = dc.id
        ORDER BY win_rate DESC
    """
    try:
        rows = _all(sql, params_step1)
        counters = [f"{r['name']} ({r['win_rate']}% WR)" for r in rows[:limit]]
        countered_by = [f"{r['name']} ({r['win_rate']}% WR)" for r in rows[-limit:]]
        return counters, countered_by
    except Exception:
        return [], []


# ── Top champions ─────────────────────────────────────────────


def query_top_champions(position: Optional[str] = None, match_type: Optional[str] = None, limit: int = 10) -> List[Dict]:
    clauses, params = [], []
    if position:
        clauses.append("ps.position = %s")
        params.append(position)
    if match_type:
        clauses.append("m.match_type = %s")
        params.append(match_type)
    where = ("WHERE " + " AND ".join(clauses)) if clauses else ""
    
    sql = f"""
        SELECT dc.name, COUNT(*) AS total, SUM(ps.win) AS wins,
               ROUND(AVG(ps.win)*100, 2) AS win_rate
        FROM player_stats ps
        JOIN matches m        ON ps.match_id    = m.match_id
        JOIN dim_champions dc ON ps.champion_id = dc.id
        {where}
        GROUP BY ps.champion_id, dc.name
        HAVING COUNT(*) > 100
        ORDER BY win_rate DESC
        LIMIT {int(limit)}
    """
    return _all(sql, params)


# ── Player stats ──────────────────────────────────────────────


def query_player_stats(
    summoner_id: str,
    last_n: int = 20,
    champion: Optional[str] = None,
    role: Optional[str] = None,
    match_type: Optional[str] = None,
) -> List[Dict]:
    role_to_db = {
        "TOP": "TOP",
        "JUNGLE": "JUNGLE",
        "MID": "MIDDLE",
        "BOT": "BOTTOM",
        "SUPPORT": "UTILITY",
    }

    if "#" in summoner_id:
        name, tag = summoner_id.split("#", 1)
        row = _one("SELECT puuid FROM dim_players WHERE name = %s AND tag = %s", [name, tag])
    else:
        row = _one("SELECT puuid FROM dim_players WHERE puuid = %s OR name = %s LIMIT 1", [summoner_id, summoner_id])

    if row is None:
        return []
    puuid = row["puuid"]

    clauses = ["ps.puuid = %s"]
    params: List[Any] = [puuid]

    if champion:
        cid = get_champion_id(champion)
        if cid:
            clauses.append("ps.champion_id = %s")
            params.append(str(cid))

    if role:
        clauses.append("ps.position = %s")
        params.append(role_to_db.get(role, role))

    if match_type:
        clauses.append("m.match_type = %s")
        params.append(match_type)

    sql = f"""
        SELECT
            ps.match_id, ps.champion_id, ps.position, ps.win,
            ps.kills, ps.deaths, ps.assists, ps.gold, ps.cs,
            ps.dmg_champs, ps.vision, ps.kda, ps.kp,
            m.duration AS duration_sec
        FROM player_stats ps
        JOIN matches m ON ps.match_id = m.match_id
        WHERE {" AND ".join(clauses)}
        ORDER BY m.timestamp DESC, ps.match_id DESC
        LIMIT {int(last_n)}
    """
    return _all(sql, params)
