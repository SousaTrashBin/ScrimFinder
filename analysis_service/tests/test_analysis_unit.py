
"""
analysis_service/tests/test_unit.py  —  Unit tests (no DB required)

All DB calls routed through BQMock.

Run:
    pytest analysis_service/tests/test_unit.py -v
"""

import pytest
from fastapi.testclient import TestClient

from analysis_service.tests.analysis_bq_mock import BQMock

pytestmark = pytest.mark.unit


@pytest.fixture
def client(monkeypatch):
    from analysis_service.main import app

    mock = BQMock(monkeypatch)
    mock.seed("dim_champions", [
        {"id": "22", "name": "Ashe"},
        {"id": "24", "name": "Jax"},
        {"id": "1", "name": "Annie"},
        {"id": "2", "name": "Olaf"},
        {"id": "3", "name": "Galio"},
    ])
    mock.seed("dim_items", [{"id": "3031", "name": "Infinity Edge"}])
    mock.seed("dim_players", [{"puuid": "P1", "name": "Rodrigo", "tag": "EUW"}])
    mock.seed("matches", [{"match_id": "M1", "match_type": "RANKED", "duration": 1800, "patch": "14.10", "timestamp": 1}])
    mock.seed("player_stats", [
        {"match_id": "M1", "puuid": "P1", "champion_id": "22", "team_id": "100", "win": 1,
         "position": "BOTTOM", "kills": 8, "deaths": 2, "assists": 9, "gold": 14000,
         "cs": 240, "dmg_champs": 21000, "vision": 30, "kda": 8.5, "kp": 0.8},
    ])
    mock.seed("player_items", [{"match_id": "M1", "puuid": "P1", "item_id": "3031", "slot": 0}])
    mock.seed("player_runes", [{"match_id": "M1", "puuid": "P1", "rune_id": "8005"}])

    # Mock model loading to avoid file system deps
    import analysis_service.routers.analysis as analysis_mod
    import numpy as np

    class ProbModel:
        def predict_proba(self, x):
            return np.array([[0.3, 0.7] for _ in range(len(x))])

    class ZeroEncoder:
        def transform(self, values):
            return np.zeros(len(values), dtype=np.int64)

    class FakeMultiLabel:
        classes_ = ["22", "24"]
        def transform(self, values):
            return np.zeros((len(values), len(self.classes_)), dtype=np.float32)

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

    monkeypatch.setattr(analysis_mod, "_load_artifact", fake_artifact)
    monkeypatch.setattr(analysis_mod, "_model_version", lambda concern: f"test-{concern}")

    return TestClient(app, raise_server_exceptions=False)


# ── Champion queries ──────────────────────────────────────────────────────────


def test_champion_queries_resolve_names(client):
    from analysis_service.champion.queries import get_champion_id, get_champion_name_by_id
    assert get_champion_id("ashe") == "22"
    assert get_champion_name_by_id("22") == "Ashe"


def test_winrate_query(client):
    from analysis_service.champion.queries import query_winrate
    result = query_winrate("22")
    assert result["total"] == 1
    assert result["wins"] == 1


def test_stats_query(client):
    from analysis_service.champion.queries import query_stats
    result = query_stats("22")
    assert "avgKda" in result


def test_top_items_query(client):
    from analysis_service.champion.queries import query_top_items
    result = query_top_items("22")
    assert "Infinity Edge" in result


# ── Analysis endpoints ────────────────────────────────────────────────────────


def test_draft_endpoint_returns_200(client):
    draft = {
        "team_blue": {
            "champions": [
                {"name": "Ashe", "role": "BOT"},
                {"name": "Jax", "role": "TOP"},
                {"name": "Annie", "role": "MID"},
                {"name": "Olaf", "role": "JUNGLE"},
                {"name": "Galio", "role": "SUPPORT"},
            ]
        },
        "team_red": {
            "champions": [
                {"name": "Ashe", "role": "BOT"},
                {"name": "Jax", "role": "TOP"},
                {"name": "Annie", "role": "MID"},
                {"name": "Olaf", "role": "JUNGLE"},
                {"name": "Galio", "role": "SUPPORT"},
            ]
        },
    }
    r = client.post("/api/v1/analysis/draft", json=draft)
    assert r.status_code == 200
    body = r.json()
    assert 0 <= body["blue_win_probability"] <= 1
    assert 0 <= body["red_win_probability"] <= 1


def test_build_endpoint_returns_200(client):
    r = client.post(
        "/api/v1/analysis/build",
        json={"champion": "Ashe", "role": "BOT", "items": ["Infinity Edge"]},
    )
    assert r.status_code == 200
    body = r.json()
    assert 0 <= body["score"] <= 100


def test_player_endpoint_returns_200(client):
    r = client.post(
        "/api/v1/analysis/player",
        json={"summoner_id": "Rodrigo#EUW", "last_n_games": 5},
    )
    assert r.status_code == 200
    body = r.json()
    assert body["matches_analyzed"] >= 0


def test_champion_endpoint_returns_200(client):
    r = client.post("/api/v1/analysis/champion", json={"champion": "Ashe"})
    assert r.status_code == 200
    body = r.json()
    assert body["champion"] == "Ashe"
    assert "stats" in body


def test_game_endpoint_returns_200(client):
    r = client.post(
        "/api/v1/analysis/game",
        json={"raw_data": {"matchId": "X1", "gameDuration": 1800, "participants": []}},
    )
    assert r.status_code == 200


def test_champion_not_found(client):
    r = client.post("/api/v1/analysis/champion", json={"champion": "NotRealChamp"})
    assert r.status_code == 404