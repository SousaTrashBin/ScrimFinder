"""
training_service/tests/test_integration.py  —  Integration tests

Tests relationships between components (DB ↔ router ↔ feature engineering ↔
training pipeline) that are NOT directly triggered by user API calls.

Two modes:
  1. BQMock mode (default):  uses in-memory mock — fast, no external deps.
  2. Emulator mode:          set BQ_EMULATOR_HOST to hit goccy/bigquery-emulator.

Run with mock:
    pytest training_service/tests/test_integration.py -v -m integration

Run with emulator (requires docker-compose.test.yml up):
    BQ_EMULATOR_HOST=http://localhost:9050     BQ_PROJECT=test-project BQ_DATASET=test_league     pytest training_service/tests/test_integration.py -v -m integration
"""

import os
import pickle
import tempfile
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

pytestmark = [pytest.mark.integration, pytest.mark.slow]

# ── Detect emulator mode ──────────────────────────────────────────────────────

EMULATOR_HOST = os.environ.get("BQ_EMULATOR_HOST")
USING_EMULATOR = bool(EMULATOR_HOST)


# ── Fixtures ──────────────────────────────────────────────────────────────────


@pytest.fixture
def client(monkeypatch):
    """App fixture that switches between BQMock and real BQ (emulator)."""
    from training_service.main import app

    if not USING_EMULATOR:
        from training_service.tests.bq_mock import BQMock
        BQMock(monkeypatch)

    # Prevent background services from starting
    monkeypatch.setattr("training_service.grpc_server.start_background_server", lambda: None)
    monkeypatch.setattr("training_service.rabbitmq_consumer.start_background_consumer", lambda: None)

    return TestClient(app, raise_server_exceptions=False)


@pytest.fixture
def sample_riot_match():
    """A valid Riot API v5 match JSON for ingestion tests."""
    return {
        "metadata": {"matchId": "EUW1_INT_001", "participants": [f"P{i}" for i in range(10)]},
        "info": {
            "gameVersion": "14.10.1",
            "queueId": 420,
            "gameDuration": 1850,
            "platformId": "EUW1",
            "participants": [
                {
                    "puuid": f"P{i}",
                    "riotIdGameName": f"Player{i}",
                    "riotIdTagline": "EUW",
                    "championName": ["Malphite", "Amumu", "Orianna", "Jinx", "Lulu",
                                     "Fiora", "Vi", "Zed", "Caitlyn", "Thresh"][i],
                    "teamId": 100 if i < 5 else 200,
                    "teamPosition": ["TOP", "JUNGLE", "MIDDLE", "BOTTOM", "UTILITY"][i % 5],
                    "win": i < 5,
                    "kills": 5 + i,
                    "deaths": 2,
                    "assists": 8,
                    "goldEarned": 10000 + i * 500,
                    "totalMinionsKilled": 180 + i * 10,
                    "totalDamageDealtToChampions": 12000 + i * 1000,
                    "visionScore": 20 + i * 2,
                    "item0": 3031,
                    "item1": 3078 if i % 2 == 0 else 3153,
                    "item2": 0,
                    "item3": 0,
                    "item4": 0,
                    "item5": 0,
                    "item6": 0,
                    "perks": {
                        "styles": [
                            {"selections": [{"perk": 8005}, {"perk": 9111}]},
                            {"selections": [{"perk": 8100}]},
                        ]
                    },
                }
                for i in range(10)
            ],
        },
    }


# ── Ingestion ↔ Feature extraction integration ────────────────────────────────


class TestIngestionFeaturePipeline:
    def test_ingest_then_extract_features(self, client, sample_riot_match):
        """A match ingested via POST /games must be extractable via POST /features/extract."""
        # 1. Ingest
        r = client.post("/api/v1/training/games", json={"data": sample_riot_match})
        assert r.status_code == 201
        game_id = r.json()["id"]

        # 2. Extract
        r2 = client.post(
            "/api/v1/training/features/extract",
            json={"game_id": game_id, "concerns": ["draft", "build", "performance"], "store": True},
        )
        assert r2.status_code == 200
        body = r2.json()
        assert len(body["features"]) == 3
        for f in body["features"]:
            assert len(f["feature_vector"]) == len(f["feature_names"])
            assert f["feature_names"]

    def test_extract_and_retrieve(self, client, sample_riot_match):
        """Stored features must be retrievable."""
        client.post("/api/v1/training/games", json={"data": sample_riot_match})
        game_id = sample_riot_match["metadata"]["matchId"]

        client.post(
            "/api/v1/training/features/extract",
            json={"game_id": game_id, "concerns": ["draft"], "store": True},
        )
        r = client.get(f"/api/v1/training/features?game_id={game_id}")
        assert r.status_code == 200
        assert r.json()["concern"] == "draft"

    def test_feature_names_are_consistent(self, client, sample_riot_match):
        """Extracting the same match twice must yield identical feature_names."""
        client.post("/api/v1/training/games", json={"data": sample_riot_match})
        game_id = sample_riot_match["metadata"]["matchId"]

        r1 = client.post(
            "/api/v1/training/features/extract",
            json={"game_id": game_id, "concerns": ["performance"], "store": False},
        )
        r2 = client.post(
            "/api/v1/training/features/extract",
            json={"game_id": game_id, "concerns": ["performance"], "store": False},
        )
        assert r1.json()["features"][0]["feature_names"] == r2.json()["features"][0]["feature_names"]


# ── Feature engineering ↔ Data loader integration ─────────────────────────────


class TestFeatureEngineeringDataLoader:
    def test_draft_vectors_shape(self, sample_riot_match):
        """build_draft_vectors must produce X with 2*champions features."""
        from training_service.core.feature_engineering import build_draft_vectors
        import numpy as np

        champion_id_map = {
            "Malphite": "1", "Amumu": "2", "Orianna": "3", "Jinx": "4", "Lulu": "5",
            "Fiora": "6", "Vi": "7", "Zed": "8", "Caitlyn": "9", "Thresh": "10",
        }
        X, y, names = build_draft_vectors([sample_riot_match], champion_id_map)
        assert X.shape[0] == 1
        assert X.shape[1] == len(names)
        assert y.shape == (1,)
        assert set(names) == {"blue_1", "blue_2", "blue_3", "blue_4", "blue_5",
                              "red_6", "red_7", "red_8", "red_9", "red_10"}

    def test_performance_vectors_shape(self, sample_riot_match):
        """build_performance_vectors must produce X with 11 features (pos + champ + 9 numeric)."""
        from training_service.core.feature_engineering import build_performance_vectors
        from sklearn.preprocessing import LabelEncoder
        import numpy as np

        # Extract performance features first
        from training_service.ingestion.feature_extractor import extract
        structured = extract(sample_riot_match)
        perfs = structured["performance"]

        # Assign champion IDs
        for p in perfs:
            p["champion_id"] = str(hash(p["champion"]) % 1000)

        pos_le = LabelEncoder()
        pos_le.fit(["TOP", "JUNGLE", "MIDDLE", "BOTTOM", "UTILITY"])
        champ_le = LabelEncoder()
        champ_le.fit([p["champion_id"] for p in perfs])

        X, y = build_performance_vectors(perfs, pos_le, champ_le)
        assert X.shape[0] == 10  # 10 players
        assert X.shape[1] == 11  # pos + champ + 9 stats
        assert y.shape == (10,)

    def test_invalid_match_handled(self):
        """Invalid match JSON must return sentinel, not crash."""
        from training_service.core.feature_engineering import extract_features
        vector, names = extract_features({"bad": "data"}, "draft")
        assert vector == [0.0]
        assert names[0].startswith("invalid:")


# ── Training job ↔ Model registry integration ─────────────────────────────────


class TestTrainingModelRegistry:
    def test_job_registers_model_on_completion(self, client, monkeypatch):
        """A completed training job must have a model_id linked."""
        from training_service.core import db

        # Seed a fake completed job with model
        job_id = "job_int_001"
        model_id = "model_int_001"
        db.create_job(job_id, "draft", "gbm", None, {"sample": 0.5})
        db.update_job(job_id, status="COMPLETED", progress=100, stage="Done",
                      model_id=model_id, completed_at="2026-05-01T00:00:00")

        r = client.get(f"/api/v1/training/jobs?job_id={job_id}")
        assert r.status_code == 200
        job = r.json()["jobs"][0]
        assert job["status"] == "COMPLETED"
        assert job["model_id"] == model_id

    def test_deploy_activates_model(self, client, monkeypatch):
        """POST /jobs/{id}/deploy must activate the linked model."""
        from training_service.core import db

        job_id = "job_int_002"
        model_id = "model_int_002"
        db.create_job(job_id, "performance", "gbm", None, {})
        db.update_job(job_id, status="COMPLETED", progress=100, stage="Done",
                      model_id=model_id, completed_at="2026-05-01T00:00:00")
        db.register_model("performance", "gbm", "v_int", "/tmp/fake.pkl",
                         {"acc": 0.7}, {}, None, ["f1"])
        # The register_model returns a new ID; we need to update the job to match
        models = db.list_models(concern="performance")
        real_mid = models[0]["id"]
        db.update_job(job_id, model_id=real_mid)

        r = client.post(f"/api/v1/training/jobs/{job_id}/deploy")
        assert r.status_code == 200
        # Check model is now active
        active = db.get_active_model("performance")
        assert active is not None
        assert active["id"] == real_mid

    def test_cannot_deploy_failed_job(self, client):
        """Deploying a failed job must return 409."""
        from training_service.core import db
        job_id = "job_int_003"
        db.create_job(job_id, "build", "random_forest", None, {})
        db.update_job(job_id, status="FAILED", error="oom", completed_at="2026-05-01T00:00:00")

        assert client.post(f"/api/v1/training/jobs/{job_id}/deploy").status_code == 409


# ── gRPC ↔ DB integration (simulated) ─────────────────────────────────────────


class TestGrpcDbIntegration:
    def test_process_match_for_training_persists_game(self, monkeypatch, sample_riot_match):
        """process_match_for_training must insert game + features into DB."""
        from training_service.tests.bq_mock import BQMock
        from training_service.grpc_server import process_match_for_training

        mock = BQMock(monkeypatch)
        # Mock the detail-filling fetch
        monkeypatch.setattr(
            "training_service.grpc_server._fetch_raw_match",
            lambda match_id: sample_riot_match,
        )

        result = process_match_for_training("EUW1_INT_001", source="matchmaking")
        assert result["success"] is True
        assert result["draft_ok"] is True
        assert result["build_ok"] is True
        assert result["perf_ok"] is True

        # Verify DB state
        games = mock.tables["games"]
        assert any(g["id"] == "EUW1_INT_001" for g in games)
        features = mock.tables["features"]
        concerns = {f["concern"] for f in features if f["game_id"] == "EUW1_INT_001"}
        assert concerns >= {"draft", "build", "performance"}

    def test_get_active_model_via_grpc(self, monkeypatch):
        """GetActiveModel gRPC must return correct model metadata."""
        from training_service.tests.bq_mock import BQMock
        from training_service.grpc_server import TrainingServiceServicer
        import grpc

        mock = BQMock(monkeypatch)
        mock.seed_models([
            {"id": "GRPC_M1", "concern": "draft", "algorithm": "gbm", "version": "v1",
             "file_path": "/tmp/m.pkl", "metrics": {}, "hyperparams": {},
             "feature_names": [], "is_active": True, "created_at": "2026-01-01", "activated_at": "2026-01-01"},
        ])

        servicer = TrainingServiceServicer()
        # Build a fake request
        from training_service.training_service_pb2 import GetActiveModelRequest
        req = GetActiveModelRequest(concern="draft")
        resp = servicer.GetActiveModel(req, None)
        assert resp.found is True
        assert resp.model_id == "GRPC_M1"
        assert resp.concern == "draft"

    def test_health_check_counts_games(self, monkeypatch):
        """HealthCheck must return correct game count."""
        from training_service.tests.bq_mock import BQMock
        from training_service.grpc_server import TrainingServiceServicer

        mock = BQMock(monkeypatch)
        mock.seed_games([{"id": f"G{i}"} for i in range(7)])

        servicer = TrainingServiceServicer()
        from training_service.training_service_pb2 import HealthCheckRequest
        resp = servicer.HealthCheck(HealthCheckRequest(), None)
        assert resp.healthy is True
        assert resp.games_ingested == 7


# ── League import integration ─────────────────────────────────────────────────


class TestLeagueImport:
    def test_import_league_assembles_participants(self, client, monkeypatch):
        """Import league must assemble participants with items and runes."""
        from training_service.tests.bq_mock import BQMock
        from training_service.core import db

        mock = BQMock(monkeypatch)
        # Seed league tables
        mock.tables["dim_champions"] = [{"id": "22", "name": "Ashe"}]
        mock.tables["dim_items"] = [{"id": "3031", "name": "Infinity Edge"}]
        mock.tables["dim_runes"] = [{"id": "8005", "name": "Press the Attack"}]
        mock.tables["matches"] = [{"match_id": "L1", "match_type": "RANKED", "duration": 1800, "patch": "14.10"}]
        mock.tables["player_stats"] = [
            {"match_id": "L1", "puuid": "P1", "champion_id": "22", "team_id": "100", "win": 1,
             "position": "BOTTOM", "kills": 8, "deaths": 2, "assists": 9, "gold": 14000,
             "cs": 240, "dmg_champs": 21000, "vision": 30, "kda": 8.5, "kp": 0.8},
        ]
        mock.tables["player_items"] = [
            {"match_id": "L1", "puuid": "P1", "item_id": "3031", "slot": 0},
        ]
        mock.tables["player_runes"] = [
            {"match_id": "L1", "puuid": "P1", "rune_id": "8005"},
        ]
        mock.tables["team_stats"] = [
            {"match_id": "L1", "team_id": "100", "win": 1, "baron": 1, "dragon": 2, "tower": 5,
             "inhibitor": 0, "horde": 0, "first_blood": 1, "first_tower": 1, "first_dragon": 1},
        ]
        mock.tables["bans"] = [
            {"match_id": "L1", "team_id": "100", "champion_id": "1", "pick_turn": 1},
        ]

        r = client.post("/api/v1/training/games/import/league", json={"limit": 1})
        assert r.status_code == 200
        body = r.json()
        assert body["imported"] == 1
        assert body["skipped"] == 0

        # Verify the game was stored
        game = db.get_game("L1")
        assert game is not None
        assert game["source"] == "league_db"