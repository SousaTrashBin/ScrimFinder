"""
training_service/tests/test_unit.py  —  Unit tests (no live DB required)

All DB calls are routed through BQMock. These run in CI even when
no BigQuery service is present.

Run:
    pytest training_service/tests/test_unit.py -v
"""

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from training_service.tests.bq_mock import BQMock

pytestmark = pytest.mark.unit


# ── Fixtures ──────────────────────────────────────────────────────────────────


@pytest.fixture
def client(monkeypatch):
    """Minimal app with BQMock — no lifespan, no real DB."""
    from training_service.routers import games, features, datasets, models, training

    mock = BQMock(monkeypatch)

    # Build a minimal FastAPI app with the same prefix structure as main.py
    # so that openapi.json is available.
    app = FastAPI(
        title="ScrimFinder Training Service",
        version="1.0.0",
        docs_url="/api/v1/training/docs",
        openapi_url="/api/v1/training/openapi.json",
    )
    app.include_router(games.router, prefix="/api/v1/training")
    app.include_router(features.router, prefix="/api/v1/training")
    app.include_router(datasets.router, prefix="/api/v1/training")
    app.include_router(models.router, prefix="/api/v1/training")
    app.include_router(training.router, prefix="/api/v1/training")

    # Seed some data
    mock.seed_games(
        [
            {
                "id": "G1",
                "source": "test",
                "patch": "14.10",
                "match_type": "RANKED",
                "duration_sec": 1800,
                "platform": "EUW",
                "raw_json": {"matchId": "G1"},
                "ingested_at": "2026-01-01T00:00:00",
            },
        ]
    )
    mock.seed_models(
        [
            {
                "id": "M1",
                "concern": "draft",
                "algorithm": "gbm",
                "version": "v1",
                "file_path": "/tmp/m1.pkl",
                "metrics": {},
                "hyperparams": {},
                "feature_names": [],
                "is_active": True,
                "created_at": "2026-01-01T00:00:00",
            },
        ]
    )

    return TestClient(app, raise_server_exceptions=False)


# ── OpenAPI schema shape ──────────────────────────────────────────────────────


def test_openapi_exposes_all_routes(client):
    r = client.get("/api/v1/training/openapi.json")
    assert r.status_code == 200, f"OpenAPI spec not available: {r.text}"
    spec = r.json()
    assert "paths" in spec, "OpenJSON missing 'paths' key"
    paths = spec["paths"]

    required = [
        "/api/v1/training/games",
        "/api/v1/training/games/batch",
        "/api/v1/training/features/extract",
        "/api/v1/training/datasets",
        "/api/v1/training/jobs",
        "/api/v1/training/models",
    ]
    for p in required:
        assert any(path.startswith(p) for path in paths), f"Missing route prefix {p}"


# ── Games router ──────────────────────────────────────────────────────────────


class TestGamesUnit:
    def test_ingest(self, client):
        r = client.post("/api/v1/training/games", json={"data": {"matchId": "G2"}})
        assert r.status_code == 201, f"Ingest failed: {r.text}"
        assert r.json()["id"] == "G2"

    def test_ingest_idempotent(self, client):
        r1 = client.post("/api/v1/training/games", json={"data": {"matchId": "G2"}})
        r2 = client.post("/api/v1/training/games", json={"data": {"matchId": "G2"}})
        assert r1.status_code == 201, f"First ingest failed: {r1.text}"
        assert r2.status_code == 201, f"Second ingest failed: {r2.text}"
        assert r1.json()["id"] == r2.json()["id"]

    def test_get_by_query_param(self, client):
        r = client.get("/api/v1/training/games?game_id=G1")
        assert r.status_code == 200
        body = r.json()
        assert "games" in body
        assert body["games"][0]["id"] == "G1"

    def test_get_by_path(self, client):
        r = client.get("/api/v1/training/games/G1")
        assert r.status_code == 200
        assert r.json()["id"] == "G1"

    def test_get_404(self, client):
        assert client.get("/api/v1/training/games/NOPE").status_code == 404

    def test_list_filtered(self, client):
        r = client.get("/api/v1/training/games?source=test")
        assert r.status_code == 200
        assert len(r.json()["games"]) >= 1

    def test_delete(self, client):
        client.post("/api/v1/training/games", json={"id": "DEL", "data": {"x": 1}})
        assert client.delete("/api/v1/training/games/DEL").status_code == 204
        assert client.get("/api/v1/training/games/DEL").status_code == 404

    def test_batch_limit(self, client):
        r = client.post(
            "/api/v1/training/games/batch",
            json={"games": [{"data": {"matchId": f"G{i}"}} for i in range(1001)]},
        )
        assert r.status_code == 422


# ── Features router ───────────────────────────────────────────────────────────


class TestFeaturesUnit:
    def test_extract_by_id(self, client):
        r = client.post(
            "/api/v1/training/features/extract",
            json={"game_id": "G1", "concerns": ["draft"], "store": False},
        )
        assert r.status_code == 200
        assert r.json()["features"][0]["concern"] == "draft"

    def test_extract_inline(self, client):
        r = client.post(
            "/api/v1/training/features/extract",
            json={"raw_data": {"matchId": "G2"}, "concerns": ["build"], "store": False},
        )
        assert r.status_code == 200

    def test_extract_404(self, client):
        assert (
            client.post(
                "/api/v1/training/features/extract",
                json={"game_id": "NOPE", "concerns": ["draft"]},
            ).status_code
            == 404
        )

    def test_get_by_query_param(self, client):
        # First store something
        r_store = client.post(
            "/api/v1/training/features/extract",
            json={"game_id": "G1", "concerns": ["draft"], "store": True},
        )
        assert r_store.status_code == 200, f"Store failed: {r_store.text}"
        r = client.get("/api/v1/training/features?game_id=G1")
        assert r.status_code == 200, f"Get failed: {r.text}"
        assert r.json()["game_id"] == "G1"

    def test_get_by_path(self, client):
        # First store something
        client.post(
            "/api/v1/training/features/extract",
            json={"game_id": "G1", "concerns": ["draft"], "store": True},
        )
        r = client.get("/api/v1/training/features/G1")
        assert r.status_code == 200
        assert r.json()["game_id"] == "G1"

    def test_get_by_path_404(self, client):
        r = client.get("/api/v1/training/features/NOPE")
        assert r.status_code == 404


# ── Datasets router ───────────────────────────────────────────────────────────


class TestDatasetsUnit:
    def test_create(self, client):
        r = client.post(
            "/api/v1/training/datasets",
            json={"name": "Test DS", "concern": "draft"},
        )
        assert r.status_code == 201
        assert r.json()["name"] == "Test DS"

    def test_get_by_query_param(self, client):
        r = client.post(
            "/api/v1/training/datasets",
            json={"name": "FindMe", "concern": "draft"},
        )
        ds_id = r.json()["id"]
        r2 = client.get(f"/api/v1/training/datasets?dataset_id={ds_id}")
        assert r2.status_code == 200
        assert r2.json()["datasets"][0]["id"] == ds_id

    def test_list_filtered(self, client):
        r = client.get("/api/v1/training/datasets?concern=draft")
        assert r.status_code == 200

    def test_delete(self, client):
        r = client.post(
            "/api/v1/training/datasets",
            json={"name": "ToDelete", "concern": "build"},
        )
        ds_id = r.json()["id"]
        assert client.delete(f"/api/v1/training/datasets/{ds_id}").status_code == 204


# ── Models router ─────────────────────────────────────────────────────────────


class TestModelsUnit:
    def test_list(self, client):
        r = client.get("/api/v1/training/models")
        assert r.status_code == 200
        assert len(r.json()["models"]) >= 1

    def test_get_by_query_param(self, client):
        r = client.get("/api/v1/training/models?model_id=M1")
        assert r.status_code == 200
        assert r.json()["models"][0]["id"] == "M1"

    def test_get_by_path(self, client):
        r = client.get("/api/v1/training/models/M1")
        assert r.status_code == 200
        assert r.json()["id"] == "M1"

    def test_get_404(self, client):
        assert client.get("/api/v1/training/models/NOPE").status_code == 404

    def test_active(self, client):
        r = client.get("/api/v1/training/models/active")
        assert r.status_code == 200
        for m in r.json()["models"]:
            assert m["is_active"] is True


# ── Training jobs router ──────────────────────────────────────────────────────


class TestTrainingJobsUnit:
    def test_create(self, client):
        r = client.post(
            "/api/v1/training/jobs", json={"concern": "draft", "sample": 0.01}
        )
        assert r.status_code == 202, f"Create job failed: {r.text}"
        assert r.json()["concern"] == "draft"

    def test_get_by_query_param(self, client):
        j = client.post(
            "/api/v1/training/jobs", json={"concern": "draft", "sample": 0.01}
        )
        assert j.status_code == 202, f"Create job failed: {j.text}"
        r = client.get(f"/api/v1/training/jobs?job_id={j.json()['id']}")
        assert r.status_code == 200, f"Get job failed: {r.text}"
        assert r.json()["jobs"][0]["id"] == j.json()["id"]

    def test_list_filtered(self, client):
        r = client.get("/api/v1/training/jobs?concern=draft")
        assert r.status_code == 200
        assert "jobs" in r.json()

    def test_invalid_concern(self, client):
        assert (
            client.post(
                "/api/v1/training/jobs", json={"concern": "invalid"}
            ).status_code
            == 422
        )

    def test_cancel_404(self, client):
        assert client.post("/api/v1/training/jobs/job_nope/cancel").status_code == 404


# ── Feature engineering ───────────────────────────────────────────────────────


class TestFeatureEngineering:
    def test_extract_draft_returns_vector_and_names(self):
        from training_service.core.feature_engineering import extract_features

        raw = {
            "metadata": {"matchId": "M1"},
            "info": {
                "gameVersion": "14.10.1",
                "queueId": 420,
                "gameDuration": 1800,
                "participants": [
                    {
                        "puuid": f"P{i}",
                        "championName": f"Champ{i}",
                        "teamId": 100 if i < 5 else 200,
                        "teamPosition": [
                            "TOP",
                            "JUNGLE",
                            "MIDDLE",
                            "BOTTOM",
                            "UTILITY",
                        ][i % 5],
                        "win": i < 5,
                        "kills": 5,
                        "deaths": 2,
                        "assists": 8,
                        "goldEarned": 12000,
                        "totalMinionsKilled": 200,
                        "totalDamageDealtToChampions": 15000,
                        "visionScore": 25,
                        "item0": 3031,
                        "item1": 0,
                        "item2": 0,
                        "item3": 0,
                        "item4": 0,
                        "item5": 0,
                        "item6": 0,
                        "perks": {"styles": [{"selections": [{"perk": 8005}]}]},
                    }
                    for i in range(10)
                ],
            },
        }
        vector, names = extract_features(raw, "draft")
        assert len(vector) == len(names)
        assert "winner" in names

    def test_extract_build_aggregate(self):
        from training_service.core.feature_engineering import extract_features

        raw = {
            "metadata": {"matchId": "M1"},
            "info": {
                "gameVersion": "14.10.1",
                "queueId": 420,
                "gameDuration": 1800,
                "participants": [
                    {
                        "puuid": f"P{i}",
                        "championName": f"Champ{i}",
                        "teamId": 100 if i < 5 else 200,
                        "teamPosition": "MIDDLE",
                        "win": True,
                        "kills": 5,
                        "deaths": 2,
                        "assists": 8,
                        "goldEarned": 12000,
                        "totalMinionsKilled": 200,
                        "totalDamageDealtToChampions": 15000,
                        "visionScore": 25,
                        "item0": 3031,
                        "item1": 3078,
                        "item2": 0,
                        "item3": 0,
                        "item4": 0,
                        "item5": 0,
                        "item6": 0,
                        "perks": {
                            "styles": [{"selections": [{"perk": 8005}, {"perk": 9111}]}]
                        },
                    }
                    for i in range(10)
                ],
            },
        }
        vector, names = extract_features(raw, "build")
        assert len(vector) == len(names)
        assert "avg_gold" in names

    def test_extract_performance_aggregate(self):
        from training_service.core.feature_engineering import extract_features

        raw = {
            "metadata": {"matchId": "M1"},
            "info": {
                "gameVersion": "14.10.1",
                "queueId": 420,
                "gameDuration": 1800,
                "participants": [
                    {
                        "puuid": f"P{i}",
                        "championName": f"Champ{i}",
                        "teamId": 100 if i < 5 else 200,
                        "teamPosition": "MIDDLE",
                        "win": True,
                        "kills": 5,
                        "deaths": 2,
                        "assists": 8,
                        "goldEarned": 12000,
                        "totalMinionsKilled": 200,
                        "totalDamageDealtToChampions": 15000,
                        "visionScore": 25,
                        "item0": 3031,
                        "item1": 0,
                        "item2": 0,
                        "item3": 0,
                        "item4": 0,
                        "item5": 0,
                        "item6": 0,
                        "perks": {"styles": [{"selections": [{"perk": 8005}]}]},
                    }
                    for i in range(10)
                ],
            },
        }
        vector, names = extract_features(raw, "performance")
        assert len(vector) == len(names)
        assert "avg_kda" in names

    def test_invalid_match_returns_sentinel(self):
        from training_service.core.feature_engineering import extract_features

        vector, names = extract_features({"bad": "data"}, "draft")
        assert vector == [0.0]
        assert names[0].startswith("invalid:")
