import sqlite3

import numpy as np
import pytest
from fastapi.testclient import TestClient

from analysis_service.champion import queries
from analysis_service.main import app
from analysis_service.routers import analysis

pytestmark = pytest.mark.integration


class ProbModel:
    def predict_proba(self, x):
        return np.array([[0.3, 0.7] for _ in range(len(x))])


class ZeroEncoder:
    def transform(self, values):
        return np.zeros(len(values), dtype=np.int64)


class FakeMultiLabel:
    classes_ = ["22", "23", "24"]

    def transform(self, values):
        return np.zeros((len(values), len(self.classes_)), dtype=np.float32)


@pytest.fixture()
def client_with_dataset(tmp_path, monkeypatch):
    db_path = tmp_path / "league_data.db"
    conn = sqlite3.connect(db_path)
    conn.execute("CREATE TABLE dim_champions (id TEXT PRIMARY KEY, name TEXT)")
    conn.execute("CREATE TABLE dim_items (id TEXT PRIMARY KEY, name TEXT)")
    conn.execute("CREATE TABLE dim_players (puuid TEXT PRIMARY KEY, name TEXT, tag TEXT)")
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
    conn.execute("CREATE TABLE player_runes (match_id TEXT, puuid TEXT, rune_id TEXT)")
    for champ_id, name in [("22", "Ashe"), ("24", "Jax"), ("1", "Annie"), ("2", "Olaf"), ("3", "Galio")]:
        conn.execute("INSERT INTO dim_champions VALUES (?, ?)", (champ_id, name))
    conn.execute("INSERT INTO dim_items VALUES ('3031', 'Infinity Edge')")
    conn.execute("INSERT INTO dim_players VALUES ('P1', 'Rodrigo', 'EUW')")
    conn.execute("INSERT INTO matches VALUES ('M1', '14.10', 1800, 1, 'RANKED')")
    conn.execute(
        "INSERT INTO player_stats VALUES "
        "('M1', 'P1', '22', '100', 1, 'BOTTOM', 8, 2, 9, 14000, 240, 21000, 30, 8.5, 0.8)"
    )
    conn.execute("INSERT INTO player_items VALUES ('M1', 'P1', '3031', 0)")
    conn.commit()
    conn.close()

    monkeypatch.setattr(queries.cfg, "LEAGUE_DB", str(db_path))
    monkeypatch.setattr(queries.cfg, "LEAGUE_DB_DSN", "")

    def fake_artifact(concern):
        if concern == "draft":
            return {"mlb": FakeMultiLabel(), "model": ProbModel()}, "test-draft"
        if concern == "build":
            return {
                "model": ProbModel(),
                "encoders": {
                    "item_mlb": FakeMultiLabel(),
                    "rune_mlb": FakeMultiLabel(),
                    "pos_le": ZeroEncoder(),
                    "champ_le": ZeroEncoder(),
                },
            }, "test-build"
        return {
            "pipeline": ProbModel(),
            "encoders": {"pos_le": ZeroEncoder(), "champ_le": ZeroEncoder()},
            "percentiles": {
                "BOT": {
                    "kda": {"p10": 1, "p25": 2, "p50": 3, "p75": 5, "p90": 8},
                    "vision": {"p10": 5, "p25": 10, "p50": 20, "p75": 30, "p90": 45},
                    "cs": {"p10": 80, "p25": 120, "p50": 180, "p75": 230, "p90": 280},
                    "dmg_champs": {"p10": 5000, "p25": 9000, "p50": 15000, "p75": 22000, "p90": 30000},
                    "kp": {"p10": 0.2, "p25": 0.35, "p50": 0.5, "p75": 0.65, "p90": 0.8},
                }
            },
        }, "test-performance"

    monkeypatch.setattr(analysis, "_load_artifact", fake_artifact)
    monkeypatch.setattr(analysis, "_model_version", lambda concern: f"test-{concern}")
    return TestClient(app, raise_server_exceptions=True)


def test_draft_build_player_and_champion_endpoints_work(client_with_dataset):
    draft = {
        "team_blue": {
            "champions": [
                {"name": name, "role": role}
                for name, role in [
                    ("Ashe", "BOT"),
                    ("Jax", "TOP"),
                    ("Annie", "MID"),
                    ("Olaf", "JUNGLE"),
                    ("Galio", "SUPPORT"),
                ]
            ]
        },
        "team_red": {
            "champions": [
                {"name": name, "role": role}
                for name, role in [
                    ("Ashe", "BOT"),
                    ("Jax", "TOP"),
                    ("Annie", "MID"),
                    ("Olaf", "JUNGLE"),
                    ("Galio", "SUPPORT"),
                ]
            ]
        },
    }

    assert client_with_dataset.post("/api/v1/analysis/draft", json=draft).status_code == 200
    assert (
        client_with_dataset.post(
            "/api/v1/analysis/build", json={"champion": "Ashe", "role": "BOT", "items": ["Infinity Edge"]}
        ).status_code
        == 200
    )
    assert (
        client_with_dataset.post(
            "/api/v1/analysis/player", json={"summoner_id": "Rodrigo#EUW", "last_n_games": 5}
        ).json()["matches_analyzed"]
        == 1
    )
    assert client_with_dataset.post("/api/v1/analysis/champion", json={"champion": "Ashe"}).status_code == 200
