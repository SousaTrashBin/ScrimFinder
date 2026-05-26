
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
    from analysis_service.main import app

    if not USING_EMULATOR:
        from analysis_service.tests.bq_mock import BQMock
        BQMock(monkeypatch)

    return TestClient(app, raise_server_exceptions=False)


# ── gRPC client integration ───────────────────────────────────────────────────


class TestGrpcIntegration:
    def test_get_active_model_grpc_unreachable(self, monkeypatch):
        """When Training Service is unreachable, get_active_model returns None."""
        import analysis_service.grpc_client as grpc_mod

        monkeypatch.setattr(grpc_mod, "_get_channel", lambda: None)
        result = grpc_mod.get_active_model("draft")
        assert result is None

    def test_health_check_grpc_unreachable(self, monkeypatch):
        """When Training Service is unreachable, health_check returns unhealthy."""
        import analysis_service.grpc_client as grpc_mod

        monkeypatch.setattr(grpc_mod, "_get_channel", lambda: None)
        result = grpc_mod.health_check_grpc()
        assert result["healthy"] is False


# ── Champion queries integration ──────────────────────────────────────────────


class TestChampionQueriesIntegration:
    def test_counters_query(self, client, monkeypatch):
        from analysis_service.tests.bq_mock import BQMock

        mock = BQMock(monkeypatch)
        mock.seed("player_stats", [
            {"match_id": f"M{i}", "puuid": "P1", "champion_id": "22", "team_id": "100", "win": 1 if i < 3 else 0,
             "position": "BOTTOM", "kills": 5, "deaths": 2, "assists": 5, "gold": 10000,
             "cs": 200, "dmg_champs": 15000, "vision": 20, "kda": 5.0, "kp": 0.5}
            for i in range(10)
        ] + [
            {"match_id": f"M{i}", "puuid": "P2", "champion_id": "24", "team_id": "200", "win": 0 if i < 3 else 1,
             "position": "TOP", "kills": 3, "deaths": 4, "assists": 2, "gold": 8000,
             "cs": 150, "dmg_champs": 10000, "vision": 15, "kda": 1.25, "kp": 0.3}
            for i in range(10)
        ])
        mock.seed("dim_champions", [
            {"id": "22", "name": "Ashe"},
            {"id": "24", "name": "Jax"},
        ])

        from analysis_service.champion.queries import query_counters
        counters, countered_by = query_counters("22", limit=3)
        assert isinstance(counters, list)
        assert isinstance(countered_by, list)

    def test_top_champions_query(self, client, monkeypatch):
        from analysis_service.tests.bq_mock import BQMock

        mock = BQMock(monkeypatch)
        mock.seed("player_stats", [
            {"match_id": f"M{i}", "puuid": f"P{i}", "champion_id": "22", "team_id": "100", "win": 1,
             "position": "BOTTOM", "kills": 5, "deaths": 2, "assists": 5, "gold": 10000,
             "cs": 200, "dmg_champs": 15000, "vision": 20, "kda": 5.0, "kp": 0.5}
            for i in range(200)
        ])
        mock.seed("dim_champions", [{"id": "22", "name": "Ashe"}])
        mock.seed("matches", [{"match_id": f"M{i}", "match_type": "RANKED", "duration": 1800, "patch": "14.10", "timestamp": i}
                              for i in range(200)])

        from analysis_service.champion.queries import query_top_champions
        result = query_top_champions(position="BOTTOM", limit=5)
        assert isinstance(result, list)
        if result:
            assert "name" in result[0]
            assert "win_rate" in result[0]