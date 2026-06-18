"""
analysis_service/tests/test_acceptance.py  —  Acceptance / contract tests

Validates that every public endpoint responds correctly for both
happy-path and expected-error scenarios. Uses BQMock.

Run:
    pytest analysis_service/tests/test_acceptance.py -v
"""

import pytest
from fastapi.testclient import TestClient

from ..tests.analysis_bq_mock import BQMock

pytestmark = pytest.mark.acceptance


@pytest.fixture
def client(monkeypatch):

    from ..main import app

    mock = BQMock(monkeypatch)
    mock.seed(
        "dim_champions",
        [
            {"id": "22", "name": "Ashe"},
            {"id": "24", "name": "Jax"},
            {"id": "1", "name": "Annie"},
            {"id": "2", "name": "Olaf"},
            {"id": "3", "name": "Galio"},
            {"id": "4", "name": "Malphite"},
            {"id": "5", "name": "Amumu"},
            {"id": "6", "name": "Orianna"},
            {"id": "7", "name": "Jinx"},
            {"id": "8", "name": "Lulu"},
            {"id": "9", "name": "Fiora"},
            {"id": "10", "name": "Vi"},
            {"id": "11", "name": "Zed"},
            {"id": "12", "name": "Caitlyn"},
            {"id": "13", "name": "Thresh"},
        ],
    )
    mock.seed(
        "dim_items",
        [
            {"id": "3031", "name": "Infinity Edge"},
            {"id": "3078", "name": "Trinity Force"},
            {"id": "3153", "name": "Blade of the Ruined King"},
        ],
    )
    mock.seed(
        "dim_players",
        [
            {"puuid": "P1", "name": "Rodrigo", "tag": "EUW"},
        ],
    )
    mock.seed(
        "matches",
        [
            {"match_id": "M1", "match_type": "RANKED", "duration": 1800, "patch": "14.10", "timestamp": 1},
            {"match_id": "M2", "match_type": "NORMAL", "duration": 2100, "patch": "14.10", "timestamp": 2},
        ],
    )
    mock.seed(
        "player_stats",
        [
            {
                "match_id": "M1",
                "puuid": "P1",
                "champion_id": "22",
                "team_id": "100",
                "win": 1,
                "position": "BOTTOM",
                "kills": 8,
                "deaths": 2,
                "assists": 9,
                "gold": 14000,
                "cs": 240,
                "dmg_champs": 21000,
                "vision": 30,
                "kda": 8.5,
                "kp": 0.8,
            },
            {
                "match_id": "M2",
                "puuid": "P1",
                "champion_id": "24",
                "team_id": "200",
                "win": 0,
                "position": "TOP",
                "kills": 3,
                "deaths": 5,
                "assists": 2,
                "gold": 9000,
                "cs": 150,
                "dmg_champs": 12000,
                "vision": 15,
                "kda": 1.0,
                "kp": 0.3,
            },
        ],
    )
    mock.seed(
        "player_items",
        [
            {"match_id": "M1", "puuid": "P1", "item_id": "3031", "slot": 0},
            {"match_id": "M2", "puuid": "P1", "item_id": "3078", "slot": 0},
        ],
    )
    mock.seed(
        "player_runes",
        [
            {"match_id": "M1", "puuid": "P1", "rune_id": "8005"},
        ],
    )

    import numpy as np

    import analysis_service.routers.analysis as analysis_mod

    class ProbModel:
        def predict_proba(self, x):
            return np.array([[0.3, 0.7] for _ in range(len(x))])

    class ZeroEncoder:
        def transform(self, values):
            return np.zeros(len(values), dtype=np.int64)

    class FakeMultiLabel:
        classes_ = ["22", "24", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13"]

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
                },
                "TOP": {
                    "kda": {"p10": 1, "p25": 2, "p50": 3, "p75": 5, "p90": 8},
                    "vision": {"p10": 5, "p25": 10, "p50": 20, "p75": 30, "p90": 45},
                    "cs": {"p10": 100, "p25": 150, "p50": 200, "p75": 250, "p90": 300},
                    "dmg_champs": {"p10": 6000, "p25": 10000, "p50": 16000, "p75": 23000, "p90": 31000},
                    "kp": {"p10": 0.2, "p25": 0.35, "p50": 0.5, "p75": 0.65, "p90": 0.8},
                },
            },
        }, "test-performance"

    monkeypatch.setattr(analysis_mod, "_load_artifact", fake_artifact)
    monkeypatch.setattr(analysis_mod, "_model_version", lambda concern: f"test-{concern}")

    return TestClient(app, raise_server_exceptions=False)


# ── System ────────────────────────────────────────────────────────────────────


def test_public_health_and_openapi_paths(client):
    assert client.get("/api/v1/analysis/health").json() == {"status": "ok"}
    paths = client.get("/api/v1/analysis/openapi.json").json()["paths"]
    for path in (
        "/api/v1/analysis/draft",
        "/api/v1/analysis/build",
        "/api/v1/analysis/player",
        "/api/v1/analysis/game",
        "/api/v1/analysis/champion",
    ):
        assert path in paths, f"Missing {path}"


def test_root_returns_ok(client):
    r = client.get("/api/v1/analysis/")
    assert r.status_code == 200
    assert r.json()["status"] == "ok"


# ── Draft ─────────────────────────────────────────────────────────────────────


class TestDraftAcceptance:
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

    def test_draft_analysis(self, client):
        r = client.post("/api/v1/analysis/draft", json=self.DRAFT)
        assert r.status_code == 200
        body = r.json()
        assert 0 <= body["blue_win_probability"] <= 1
        assert 0 <= body["red_win_probability"] <= 1
        assert body["blue_win_probability"] + body["red_win_probability"] == pytest.approx(1.0, abs=0.01)
        assert "model_version" in body

    def test_invalid_role(self, client):
        bad = {**self.DRAFT, "team_blue": {"champions": [{"name": "X", "role": "INVALID"}]}}
        assert client.post("/api/v1/analysis/draft", json=bad).status_code == 422

    def test_missing_team(self, client):
        assert client.post("/api/v1/analysis/draft", json={"team_blue": self.DRAFT["team_blue"]}).status_code == 422


# ── Build ─────────────────────────────────────────────────────────────────────


class TestBuildAcceptance:
    def test_build_analysis(self, client):
        r = client.post(
            "/api/v1/analysis/build",
            json={"champion": "Jinx", "items": ["Infinity Edge", "Trinity Force"]},
        )
        assert r.status_code == 200
        body = r.json()
        assert 0 <= body["score"] <= 100
        assert body["champion"] == "Jinx"

    def test_no_items(self, client):
        assert client.post("/api/v1/analysis/build", json={"champion": "Jinx", "items": []}).status_code == 422

    def test_with_enemies(self, client):
        r = client.post(
            "/api/v1/analysis/build",
            json={
                "champion": "Jinx",
                "items": ["Infinity Edge"],
                "enemy_composition": ["Malphite", "Galio"],
            },
        )
        assert r.status_code == 200


# ── Player ────────────────────────────────────────────────────────────────────


class TestPlayerAcceptance:
    def test_player_analysis(self, client):
        r = client.post(
            "/api/v1/analysis/player",
            json={"summoner_id": "Rodrigo#EUW", "last_n_games": 10},
        )
        assert r.status_code == 200
        body = r.json()
        assert body["matches_analyzed"] >= 0
        assert "tips" in body

    def test_player_with_filters(self, client):
        r = client.post(
            "/api/v1/analysis/player",
            json={"summoner_id": "Rodrigo#EUW", "champion": "Ashe", "role": "BOT", "last_n_games": 5},
        )
        assert r.status_code == 200


# ── Game ──────────────────────────────────────────────────────────────────────


class TestGameAcceptance:
    def test_inline_game(self, client):
        r = client.post(
            "/api/v1/analysis/game",
            json={"raw_data": {"matchId": "X1", "gameDuration": 1800, "participants": []}},
        )
        assert r.status_code == 200
        body = r.json()
        assert body["game_id"] == "X1"

    def test_no_source(self, client):
        assert client.post("/api/v1/analysis/game", json={}).status_code == 422


# ── Champion ──────────────────────────────────────────────────────────────────


class TestChampionAcceptance:
    def test_known_champion(self, client):
        r = client.post("/api/v1/analysis/champion", json={"champion": "Ashe"})
        assert r.status_code == 200
        body = r.json()
        assert body["champion"] == "Ashe"
        assert "stats" in body
        assert "tier" in body

    def test_unknown_champion(self, client):
        r = client.post("/api/v1/analysis/champion", json={"champion": "NotAChampXYZ"})
        assert r.status_code == 404

    def test_champion_with_position(self, client):
        r = client.post(
            "/api/v1/analysis/champion",
            json={"champion": "Ashe", "position": "BOT", "match_type": "RANKED"},
        )
        assert r.status_code == 200
