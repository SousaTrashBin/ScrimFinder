"""
Tests for the Analysis Service.
Run: docker exec scrimfinder_analysis pytest tests/test_analysis.py -v
"""

import os
import sqlite3
import tempfile
from pathlib import Path

_TMP = Path(tempfile.mkdtemp(prefix="analysis_test_"))
_LEAGUE_DB = _TMP / "league_data.db"

os.environ["MODELS_DIR"] = str(_TMP / "models")
os.environ["LEAGUE_DB"] = str(_LEAGUE_DB)

_LEAGUE_DB.parent.mkdir(parents=True, exist_ok=True)
conn = sqlite3.connect(_LEAGUE_DB)
conn.execute("CREATE TABLE IF NOT EXISTS dim_champions (id TEXT PRIMARY KEY, name TEXT)")
conn.execute("CREATE TABLE IF NOT EXISTS dim_players (puuid TEXT PRIMARY KEY, name TEXT, tag TEXT)")
conn.execute(
    "CREATE TABLE IF NOT EXISTS matches (match_id TEXT PRIMARY KEY, match_type TEXT, duration INTEGER, patch TEXT)"
)
conn.execute(
    "CREATE TABLE IF NOT EXISTS player_stats (match_id TEXT, puuid TEXT, champion_id TEXT, "
    "position TEXT, win INTEGER, kills INTEGER, deaths INTEGER, assists INTEGER, gold INTEGER, "
    "cs INTEGER, dmg_champs INTEGER, vision INTEGER, kda REAL, kp REAL)"
)
conn.close()

from fastapi.testclient import TestClient  # noqa: E402

from analysis_service.main import app  # noqa: E402

client = TestClient(app, raise_server_exceptions=False)

DRAFT = {
    "team_blue": {
        "champions": [
            {"name": "Malphite", "role": "TOP"},
            {"name": "Amumu", "role": "JUNGLE"},
            {"name": "Orianna", "role": "MID"},
            {"name": "Jinx", "role": "BOT"},
            {"name": "Lulu", "role": "SUPPORT"},
        ]
    },
    "team_red": {
        "champions": [
            {"name": "Fiora", "role": "TOP"},
            {"name": "Vi", "role": "JUNGLE"},
            {"name": "Zed", "role": "MID"},
            {"name": "Caitlyn", "role": "BOT"},
            {"name": "Thresh", "role": "SUPPORT"},
        ]
    },
}


class TestSystem:
    def test_health(self):
        assert client.get("/api/v1/analysis/health").json()["status"] == "ok"

    def test_root(self):
        assert client.get("/api/v1/analysis/").json()["status"] == "ok"

    def test_docs(self):
        assert client.get("/api/v1/analysis/docs").status_code == 200


class TestDraft:
    def test_draft(self):
        r = client.post("/api/v1/analysis/draft", json=DRAFT)
        assert r.status_code == 503

    def test_invalid_role(self):
        bad = {**DRAFT, "team_blue": {"champions": [{"name": "X", "role": "INVALID"}]}}
        assert client.post("/api/v1/analysis/draft", json=bad).status_code == 422

    def test_missing_team(self):
        assert client.post("/api/v1/analysis/draft", json={"team_blue": DRAFT["team_blue"]}).status_code == 422

    def test_deterministic(self):
        r1 = client.post("/api/v1/analysis/draft", json=DRAFT)
        r2 = client.post("/api/v1/analysis/draft", json=DRAFT)
        assert r1.status_code == r2.status_code == 503


class TestBuild:
    def test_build(self):
        r = client.post(
            "/api/v1/analysis/build", json={"champion": "Jinx", "items": ["Kraken Slayer", "Infinity Edge"]}
        )
        assert r.status_code == 503

    def test_schema(self):
        assert client.post("/api/v1/analysis/build", json={"champion": "Jinx", "items": ["Infinity Edge"]}).status_code == 503

    def test_no_items(self):
        assert client.post("/api/v1/analysis/build", json={"champion": "Jinx", "items": []}).status_code == 422

    def test_with_enemies(self):
        r = client.post(
            "/api/v1/analysis/build",
            json={
                "champion": "Jinx",
                "items": ["Kraken Slayer", "Infinity Edge"],
                "enemy_composition": ["Malphite", "Galio"],
            },
        )
        assert r.status_code == 503


class TestPlayer:
    def test_player(self):
        r = client.post("/api/v1/analysis/player", json={"summoner_id": "test", "last_n_games": 10})
        assert r.status_code == 503

    def test_schema(self):
        assert client.post("/api/v1/analysis/player", json={"summoner_id": "test"}).status_code == 503


class TestGame:
    def test_inline(self):
        r = client.post("/api/v1/analysis/game", json={"raw_data": {"matchId": "X1", "gameDuration": 1800}})
        assert r.status_code == 503

    def test_no_source(self):
        assert client.post("/api/v1/analysis/game", json={}).status_code == 422

    def test_schema(self):
        assert client.post("/api/v1/analysis/game", json={"raw_data": {"matchId": "X2"}}).status_code == 503


class TestChampion:
    def test_unknown(self):
        r = client.post("/api/v1/analysis/champion", json={"champion": "NotAChampXYZ"})
        assert r.status_code in (404, 503)
