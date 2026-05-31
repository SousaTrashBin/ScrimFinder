"""
analysis_service/tests/test_integration.py  —  Integration tests

Tests cross-service relationships (Analysis ↔ Training gRPC, Analysis ↔ BQ).
Uses BQMock by default; set BQ_EMULATOR_HOST to use goccy/bigquery-emulator.

Run:
    pytest analysis_service/tests/test_integration.py -v -m integration
"""

import os

import pytest
from fastapi.testclient import TestClient

pytestmark = [pytest.mark.integration, pytest.mark.slow]

EMULATOR_HOST = os.environ.get("BQ_EMULATOR_HOST")
USING_EMULATOR = bool(EMULATOR_HOST)


@pytest.fixture
def client(monkeypatch):
    from ..main import app

    if not USING_EMULATOR:
        from .analysis_bq_mock import BQMock

        mock = BQMock(monkeypatch)
        # Seed minimal data so endpoints don't 503
        mock.seed(
            "dim_champions",
            [
                {"id": "22", "name": "Ashe"},
                {"id": "24", "name": "Jax"},
            ],
        )
        mock.seed("dim_items", [{"id": "3031", "name": "Infinity Edge"}])
        mock.seed("dim_players", [{"puuid": "P1", "name": "Rodrigo", "tag": "EUW"}])
        mock.seed(
            "matches", [{"match_id": "M1", "match_type": "RANKED", "duration": 1800, "patch": "14.10", "timestamp": 1}]
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
            ],
        )
        mock.seed("player_items", [{"match_id": "M1", "puuid": "P1", "item_id": "3031", "slot": 0}])

    return TestClient(app, raise_server_exceptions=False)


# ── Big Query info retrieval integration ───────────────────────────────────────────────────


class TestModelQueryIntegration:
    def test_get_active_model_from_bq_mock(self, monkeypatch):
        """When a model is seeded in BQ, get_active_model returns it."""
        from ..core.db import get_active_model
        from .analysis_bq_mock import BQMock

        mock = BQMock(monkeypatch)
        mock.seed(
            "models",
            [
                {
                    "id": "model_draft_001",
                    "concern": "draft",
                    "algorithm": "gbm",
                    "version": "2026-W20",
                    "file_path": "/tmp/fake.pkl",
                    "metrics": '{"auc": 0.82}',
                    "hyperparams": "{}",
                    "feature_names": "[]",
                    "is_active": True,
                    "created_at": "2026-05-20T10:00:00",
                    "activated_at": "2026-05-20T10:00:00",
                }
            ],
        )

        result = get_active_model("draft")
        assert result is not None
        assert result["version"] == "2026-W20"
        assert result["is_active"] is True

    def test_get_active_model_missing(self, monkeypatch):
        """When no active model exists, get_active_model returns None."""
        from ..core.db import get_active_model
        from .analysis_bq_mock import BQMock

        BQMock(monkeypatch)
        result = get_active_model("performance")
        assert result is None


# ── Champion queries integration ──────────────────────────────────────────────


class TestChampionQueriesIntegration:
    def test_counters_query(self, client, monkeypatch):
        from .analysis_bq_mock import BQMock

        mock = BQMock(monkeypatch)
        mock.seed(
            "player_stats",
            [
                {
                    "match_id": f"M{i}",
                    "puuid": "P1",
                    "champion_id": "22",
                    "team_id": "100",
                    "win": 1 if i < 3 else 0,
                    "position": "BOTTOM",
                    "kills": 5,
                    "deaths": 2,
                    "assists": 5,
                    "gold": 10000,
                    "cs": 200,
                    "dmg_champs": 15000,
                    "vision": 20,
                    "kda": 5.0,
                    "kp": 0.5,
                }
                for i in range(10)
            ]
            + [
                {
                    "match_id": f"M{i}",
                    "puuid": "P2",
                    "champion_id": "24",
                    "team_id": "200",
                    "win": 0 if i < 3 else 1,
                    "position": "TOP",
                    "kills": 3,
                    "deaths": 4,
                    "assists": 2,
                    "gold": 8000,
                    "cs": 150,
                    "dmg_champs": 10000,
                    "vision": 15,
                    "kda": 1.25,
                    "kp": 0.3,
                }
                for i in range(10)
            ],
        )
        mock.seed(
            "dim_champions",
            [
                {"id": "22", "name": "Ashe"},
                {"id": "24", "name": "Jax"},
            ],
        )

        from ..champion.queries import query_counters

        counters, countered_by = query_counters("22", limit=3)
        assert isinstance(counters, list)
        assert isinstance(countered_by, list)

    def test_top_champions_query(self, client, monkeypatch):
        from .analysis_bq_mock import BQMock

        mock = BQMock(monkeypatch)
        mock.seed(
            "player_stats",
            [
                {
                    "match_id": f"M{i}",
                    "puuid": f"P{i}",
                    "champion_id": "22",
                    "team_id": "100",
                    "win": 1,
                    "position": "BOTTOM",
                    "kills": 5,
                    "deaths": 2,
                    "assists": 5,
                    "gold": 10000,
                    "cs": 200,
                    "dmg_champs": 15000,
                    "vision": 20,
                    "kda": 5.0,
                    "kp": 0.5,
                }
                for i in range(200)
            ],
        )
        mock.seed("dim_champions", [{"id": "22", "name": "Ashe"}])
        mock.seed(
            "matches",
            [
                {"match_id": f"M{i}", "match_type": "RANKED", "duration": 1800, "patch": "14.10", "timestamp": i}
                for i in range(200)
            ],
        )

        from ..champion.queries import query_top_champions

        result = query_top_champions(position="BOTTOM", limit=5)
        assert isinstance(result, list)
        # Note: BQMock does not support GROUP BY / HAVING; this test validates
        # the query runs without error and returns a list.
eturns a list.
