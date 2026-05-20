import sqlite3

import pytest

from analysis_service.champion import queries

pytestmark = pytest.mark.unit


def test_champion_queries_read_sqlite_import_source(tmp_path, monkeypatch):
    db_path = tmp_path / "league_data.db"
    conn = sqlite3.connect(db_path)
    conn.execute("CREATE TABLE dim_champions (id TEXT PRIMARY KEY, name TEXT)")
    conn.execute("CREATE TABLE dim_items (id TEXT PRIMARY KEY, name TEXT)")
    conn.execute(
        "CREATE TABLE matches (match_id TEXT PRIMARY KEY, patch TEXT, duration INTEGER, "
        "timestamp INTEGER, match_type TEXT)"
    )
    conn.execute(
        "CREATE TABLE player_stats (match_id TEXT, puuid TEXT, champion_id TEXT, team_id TEXT, win INTEGER, "
        "position TEXT, kills INTEGER, deaths INTEGER, assists INTEGER, gold INTEGER, cs INTEGER, "
        "dmg_champs INTEGER, vision INTEGER, kda REAL, kp REAL)"
    )
    conn.execute("CREATE TABLE player_items (match_id TEXT, puuid TEXT, item_id TEXT, slot INTEGER)")
    conn.execute("INSERT INTO dim_champions VALUES ('22', 'Ashe')")
    conn.execute("INSERT INTO dim_items VALUES ('3031', 'Infinity Edge')")
    conn.execute("INSERT INTO matches VALUES ('M1', '14.10', 1800, 1, 'RANKED')")
    conn.execute(
        "INSERT INTO player_stats VALUES "
        "('M1', 'P1', '22', '100', 1, 'BOTTOM', 5, 1, 7, 12000, 220, 18000, 25, 12.0, 0.75)"
    )
    conn.execute("INSERT INTO player_items VALUES ('M1', 'P1', '3031', 0)")
    conn.commit()
    conn.close()

    monkeypatch.setattr(queries.cfg, "LEAGUE_DB", str(db_path))
    monkeypatch.setattr(queries.cfg, "LEAGUE_DB_DSN", "")

    assert queries.get_champion_id("ashe") == "22"
    assert queries.query_winrate("22")["total"] == 1
    assert queries.query_stats("22")["avgKda"] == 12.0
    assert queries.query_top_items("22") == ["Infinity Edge"]
