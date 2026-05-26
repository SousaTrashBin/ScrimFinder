"""
training_service/tests/test_acceptance.py  —  Acceptance / contract tests

Validates that every public endpoint responds correctly for both
happy-path and expected-error scenarios. Uses BQMock so no live DB needed.

Run:
    pytest training_service/tests/test_acceptance.py -v
"""

import pytest
from fastapi.testclient import TestClient

from training_service.tests.bq_mock import BQMock

pytestmark = pytest.mark.acceptance


@pytest.fixture
def client(monkeypatch):
    from training_service.main import app
    from training_service.core import db

    mock = BQMock(monkeypatch)
    # Seed realistic data
    mock.seed_games([
        {
            "id": "EUW1_TEST_001",
            "source": "test",
            "patch": "14.10",
            "match_type": "RANKED",
            "duration_sec": 1823,
            "platform": "EUW1",
            "raw_json": {
                "metadata": {"matchId": "EUW1_TEST_001"},
                "info": {
                    "gameVersion": "14.10.1",
                    "queueId": 420,
                    "gameDuration": 1823,
                    "participants": [
                        {"puuid": f"P{i}", "championName": f"Champ{i}", "teamId": 100 if i < 5 else 200,
                         "teamPosition": ["TOP", "JUNGLE", "MIDDLE", "BOTTOM", "UTILITY"][i % 5],
                         "win": i < 5, "kills": 5, "deaths": 2, "assists": 8,
                         "goldEarned": 12000, "totalMinionsKilled": 200,
                         "totalDamageDealtToChampions": 15000, "visionScore": 25,
                         "item0": 3031, "item1": 0, "item2": 0, "item3": 0, "item4": 0, "item5": 0, "item6": 0,
                         "perks": {"styles": [{"selections": [{"perk": 8005}]}]}}
                        for i in range(10)
                    ],
                },
            },
            "ingested_at": "2026-05-01T12:00:00",
        },
    ])
    mock.seed_models([
        {"id": "MODEL_1", "concern": "draft", "algorithm": "gbm", "version": "2026-W20-20260524T185912",
         "file_path": "/tmp/draft.pkl", "metrics": {"accuracy": 0.72}, "hyperparams": {"n_estimators": 100},
         "feature_names": ["blue_22", "red_22"], "is_active": True, "created_at": "2026-05-01T00:00:00"},
    ])

    # Prevent lifespan from trying to start gRPC / RabbitMQ
    monkeypatch.setattr("training_service.grpc_server.start_background_server", lambda: None)
    monkeypatch.setattr("training_service.rabbitmq_consumer.start_background_consumer", lambda: None)

    return TestClient(app, raise_server_exceptions=False)


# ── System ────────────────────────────────────────────────────────────────────


def test_public_health_and_openapi_paths(client):
    assert client.get("/api/v1/training/health").json() == {"status": "ok"}
    spec = client.get("/api/v1/training/openapi.json").json()
    paths = spec["paths"]
    for path in (
        "/api/v1/training/games",
        "/api/v1/training/features/extract",
        "/api/v1/training/jobs",
        "/api/v1/training/models",
        "/api/v1/training/datasets",
    ):
        assert any(p.startswith(path) for p in paths), f"Missing {path}"


def test_root_includes_counts(client):
    r = client.get("/api/v1/training/")
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "ok"
    assert "games_ingested" in body
    assert "active_models" in body


# ── Games ─────────────────────────────────────────────────────────────────────


class TestGamesAcceptance:
    def test_ingest_and_retrieve(self, client):
        r = client.post("/api/v1/training/games", json={"data": {"matchId": "G_NEW"}})
        assert r.status_code == 201
        assert r.json()["id"] == "G_NEW"

        r2 = client.get("/api/v1/training/games/G_NEW")
        assert r2.status_code == 200
        assert r2.json()["raw_json"]["matchId"] == "G_NEW"

    def test_get_by_query_param(self, client):
        r = client.get("/api/v1/training/games?game_id=EUW1_TEST_001")
        assert r.status_code == 200
        assert r.json()["games"][0]["id"] == "EUW1_TEST_001"

    def test_list_with_filters(self, client):
        r = client.get("/api/v1/training/games?source=test&patch=14.10")
        assert r.status_code == 200
        assert "games" in r.json()
        assert "meta" in r.json()

    def test_404_on_missing(self, client):
        assert client.get("/api/v1/training/games/NO_SUCH_GAME").status_code == 404

    def test_delete(self, client):
        client.post("/api/v1/training/games", json={"id": "DEL_ME", "data": {"x": 1}})
        assert client.delete("/api/v1/training/games/DEL_ME").status_code == 204
        assert client.get("/api/v1/training/games/DEL_ME").status_code == 404

    def test_batch_ingest(self, client):
        r = client.post(
            "/api/v1/training/games/batch",
            json={"games": [{"data": {"matchId": f"B{i}"}} for i in range(5)]},
        )
        assert r.status_code == 201
        assert r.json()["ingested"] == 5

    def test_batch_over_limit(self, client):
        r = client.post(
            "/api/v1/training/games/batch",
            json={"games": [{"data": {"matchId": f"B{i}"}} for i in range(1001)]},
        )
        assert r.status_code == 422


# ── Features ──────────────────────────────────────────────────────────────────


class TestFeaturesAcceptance:
    def test_extract_by_game_id(self, client):
        r = client.post(
            "/api/v1/training/features/extract",
            json={"game_id": "EUW1_TEST_001", "concerns": ["draft", "build", "performance"], "store": True},
        )
        assert r.status_code == 200
        feats = r.json()["features"]
        assert len(feats) == 3
        assert all(f["feature_vector"] for f in feats)
        assert all(f["feature_names"] for f in feats)

    def test_extract_inline(self, client):
        r = client.post(
            "/api/v1/training/features/extract",
            json={"raw_data": {"matchId": "INLINE"}, "concerns": ["draft"], "store": False},
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

    def test_extract_422_no_source(self, client):
        assert (
            client.post(
                "/api/v1/training/features/extract", json={"concerns": ["draft"]}
            ).status_code
            == 422
        )

    def test_get_by_query_after_store(self, client):
        client.post(
            "/api/v1/training/features/extract",
            json={"game_id": "EUW1_TEST_001", "concerns": ["draft"], "store": True},
        )
        r = client.get("/api/v1/training/features?game_id=EUW1_TEST_001")
        assert r.status_code == 200
        assert r.json()["game_id"] == "EUW1_TEST_001"


# ── Datasets ──────────────────────────────────────────────────────────────────


class TestDatasetsAcceptance:
    def test_create_and_get(self, client):
        r = client.post(
            "/api/v1/training/datasets",
            json={"name": "Acceptance DS", "concern": "draft", "description": "test"},
        )
        assert r.status_code == 201
        ds_id = r.json()["id"]

        r2 = client.get(f"/api/v1/training/datasets?dataset_id={ds_id}")
        assert r2.status_code == 200
        assert r2.json()["datasets"][0]["name"] == "Acceptance DS"

    def test_list_filtered(self, client):
        r = client.get("/api/v1/training/datasets?concern=draft")
        assert r.status_code == 200

    def test_build_and_rebuild(self, client):
        r = client.post(
            "/api/v1/training/datasets/build",
            json={"name": "BuildMe", "concern": "performance"},
        )
        assert r.status_code == 202
        ds_id = r.json()["id"]

        r2 = client.post(f"/api/v1/training/datasets/{ds_id}/build")
        assert r2.status_code == 202

    def test_delete(self, client):
        r = client.post(
            "/api/v1/training/datasets",
            json={"name": "ToDelete", "concern": "build"},
        )
        ds_id = r.json()["id"]
        assert client.delete(f"/api/v1/training/datasets/{ds_id}").status_code == 204
        assert client.get(f"/api/v1/training/datasets?dataset_id={ds_id}").status_code == 404


# ── Training Jobs ─────────────────────────────────────────────────────────────


class TestTrainingJobsAcceptance:
    def test_create_and_get(self, client):
        r = client.post(
            "/api/v1/training/jobs", json={"concern": "draft", "sample": 0.01}
        )
        assert r.status_code == 202
        job = r.json()
        assert job["concern"] == "draft"
        assert job["status"] in ("PENDING", "RUNNING")

        r2 = client.get(f"/api/v1/training/jobs?job_id={job['id']}")
        assert r2.status_code == 200
        assert r2.json()["jobs"][0]["id"] == job["id"]

    def test_all_concerns_accepted(self, client):
        for c in ["draft", "build", "performance"]:
            r = client.post(
                "/api/v1/training/jobs", json={"concern": c, "sample": 0.01}
            )
            assert r.status_code == 202, f"Concern {c} failed"

    def test_list_filtered(self, client):
        r = client.get("/api/v1/training/jobs?concern=draft")
        assert r.status_code == 200
        assert "jobs" in r.json()

    def test_cancel(self, client):
        j = client.post(
            "/api/v1/training/jobs", json={"concern": "draft", "sample": 0.01}
        ).json()
        r = client.post(f"/api/v1/training/jobs/{j['id']}/cancel")
        assert r.status_code == 200
        assert r.json()["status"] in ("CANCELLED", "PENDING")

    def test_delete_job(self, client):
        j = client.post(
            "/api/v1/training/jobs", json={"concern": "build", "sample": 0.01}
        ).json()
        assert client.delete(f"/api/v1/training/jobs/{j['id']}").status_code == 204
        assert client.get(f"/api/v1/training/jobs?job_id={j['id']}").status_code == 404

    def test_invalid_concern(self, client):
        assert (
            client.post(
                "/api/v1/training/jobs", json={"concern": "invalid"}
            ).status_code
            == 422
        )


# ── Models ────────────────────────────────────────────────────────────────────


class TestModelsAcceptance:
    def test_list_and_get(self, client):
        r = client.get("/api/v1/training/models")
        assert r.status_code == 200
        assert len(r.json()["models"]) >= 1

    def test_get_by_query_param(self, client):
        r = client.get("/api/v1/training/models?model_id=MODEL_1")
        assert r.status_code == 200
        assert r.json()["models"][0]["id"] == "MODEL_1"

    def test_get_by_path(self, client):
        r = client.get("/api/v1/training/models/MODEL_1")
        assert r.status_code == 200
        assert r.json()["id"] == "MODEL_1"

    def test_active_filter(self, client):
        r = client.get("/api/v1/training/models/active")
        assert r.status_code == 200
        for m in r.json()["models"]:
            assert m["is_active"] is True

    def test_activate_deactivate_cycle(self, client):
        # Seed an inactive model
        from training_service.core import db
        db.register_model("build", "random_forest", "v2", "/tmp/b.pkl",
                         {"acc": 0.6}, {}, "ds1", ["f1"])
        # Find it
        models = db.list_models(concern="build")
        mid = models[0]["id"]

        r1 = client.post(f"/api/v1/training/models/{mid}/activate")
        assert r1.status_code == 200
        assert r1.json()["is_active"] is True

        r2 = client.post(f"/api/v1/training/models/{mid}/deactivate")
        assert r2.status_code == 200
        assert r2.json()["is_active"] is False

    def test_delete_404(self, client):
        assert client.delete("/api/v1/training/models/NOPE").status_code == 404

    def test_cannot_delete_active(self, client):
        # MODEL_1 is active
        assert client.delete("/api/v1/training/models/MODEL_1").status_code == 409