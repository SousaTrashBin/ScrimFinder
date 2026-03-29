"""
Tests for the Training Service.
Run: docker exec scrimfinder_training pytest tests/test_training.py -v -m "not slow"
"""

import os
import tempfile
import time
from pathlib import Path

_TMP = Path(tempfile.mkdtemp(prefix="train_test_"))
os.environ["PLATFORM_DB"] = str(_TMP / "platform.db")
os.environ["MODELS_DIR"] = str(_TMP / "models")
os.environ["GAMES_DIR"] = str(_TMP / "games")
os.environ["DATASETS_DIR"] = str(_TMP / "datasets")

from fastapi.testclient import TestClient  # noqa: E402

from training_service.main import app  # noqa: E402

client = TestClient(app, raise_server_exceptions=False)

GAME = {
    "matchId": "EUW1_TEST_001",
    "gameVersion": "14.10.1",
    "gameType": "RANKED",
    "gameDuration": 1823,
}
GAME2 = {
    "matchId": "EUW1_TEST_002",
    "gameVersion": "14.10.1",
    "gameType": "NORMAL",
    "gameDuration": 2100,
}


class TestSystem:
    def test_health(self):
        assert client.get("/health").json()["status"] == "ok"

    def test_root(self):
        assert client.get("/").json()["status"] == "ok"

    def test_docs(self):
        assert client.get("/docs").status_code == 200


class TestGames:
    def test_ingest(self):
        r = client.post("/games", json={"data": GAME})
        assert r.status_code == 201
        assert r.json()["id"] == "EUW1_TEST_001"
        assert r.json()["match_type"] == "RANKED"

    def test_ingest_idempotent(self):
        r1 = client.post("/games", json={"data": GAME})
        r2 = client.post("/games", json={"data": GAME})
        assert r1.json()["id"] == r2.json()["id"]

    def test_ingest_explicit_id(self):
        r = client.post("/games", json={"id": "MY_ID", "data": {"x": 1}})
        assert r.json()["id"] == "MY_ID"

    def test_batch(self):
        r = client.post(
            "/games/batch", json={"games": [{"data": GAME}, {"data": GAME2}]}
        )
        assert r.status_code == 201
        assert r.json()["ingested"] == 2

    def test_batch_limit(self):
        r = client.post(
            "/games/batch",
            json={"games": [{"data": {"matchId": f"G{i}"}} for i in range(1001)]},
        )
        assert r.status_code == 422

    def test_get(self):
        client.post("/games", json={"data": GAME})
        r = client.get("/games/EUW1_TEST_001")
        assert r.status_code == 200
        assert r.json()["raw_json"]["matchId"] == "EUW1_TEST_001"

    def test_get_404(self):
        assert client.get("/games/NOPE_XYZ").status_code == 404

    def test_list(self):
        r = client.get("/games")
        assert r.status_code == 200
        assert "games" in r.json()
        assert "meta" in r.json()

    def test_delete(self):
        client.post("/games", json={"id": "DEL_ME", "data": {"x": 1}})
        assert client.delete("/games/DEL_ME").status_code == 204
        assert client.get("/games/DEL_ME").status_code == 404

    def test_delete_404(self):
        assert client.delete("/games/NOPE_XYZ").status_code == 404


class TestFeatures:
    def setup_method(self):
        client.post("/games", json={"data": GAME})

    def test_extract_by_id(self):
        r = client.post(
            "/features/extract",
            json={"game_id": "EUW1_TEST_001", "concerns": ["draft"], "store": False},
        )
        assert r.status_code == 200
        assert r.json()["features"][0]["concern"] == "draft"

    def test_extract_inline(self):
        r = client.post(
            "/features/extract",
            json={"raw_data": GAME, "concerns": ["build"], "store": False},
        )
        assert r.status_code == 200

    def test_extract_404(self):
        assert (
            client.post(
                "/features/extract", json={"game_id": "NOPE", "concerns": ["draft"]}
            ).status_code
            == 404
        )

    def test_extract_422(self):
        assert (
            client.post("/features/extract", json={"concerns": ["draft"]}).status_code
            == 422
        )

    def test_get_after_store(self):
        client.post(
            "/features/extract",
            json={"game_id": "EUW1_TEST_001", "concerns": ["draft"], "store": True},
        )
        r = client.get("/features/EUW1_TEST_001")
        assert r.status_code == 200

    def test_get_404(self):
        assert client.get("/features/NOPE_XYZ").status_code == 404


class TestDatasets:
    def test_create(self):
        r = client.post(
            "/datasets", json={"name": "Test DS", "concern": "draft", "filters": {}}
        )
        assert r.status_code == 201
        assert r.json()["id"].startswith("ds_")

    def test_build(self):
        r = client.post(
            "/datasets/build",
            json={"name": "Built DS", "concern": "build", "filters": {}},
        )
        assert r.status_code == 202

    def test_list(self):
        assert "datasets" in client.get("/datasets").json()

    def test_get_404(self):
        assert client.get("/datasets/ds_nope").status_code == 404

    def test_delete(self):
        r = client.post(
            "/datasets", json={"name": "Del DS", "concern": "draft", "filters": {}}
        )
        ds_id = r.json()["id"]
        assert client.delete(f"/datasets/{ds_id}").status_code == 204
        assert client.get(f"/datasets/{ds_id}").status_code == 404

    def test_delete_404(self):
        assert client.delete("/datasets/ds_nope").status_code == 404


class TestTrainingJobs:
    def test_create(self):
        r = client.post("/training/jobs", json={"concern": "draft", "sample": 0.01})
        assert r.status_code == 202
        assert r.json()["concern"] == "draft"
        assert r.json()["status"] in ("PENDING", "RUNNING")

    def test_all_concerns(self):
        for c in ["draft", "build", "performance"]:
            assert (
                client.post(
                    "/training/jobs", json={"concern": c, "sample": 0.01}
                ).status_code
                == 202
            )

    def test_list(self):
        assert "jobs" in client.get("/training/jobs").json()

    def test_get(self):
        j = client.post(
            "/training/jobs", json={"concern": "draft", "sample": 0.01}
        ).json()
        r = client.get(f"/training/jobs/{j['id']}")
        assert r.status_code == 200
        assert r.json()["id"] == j["id"]

    def test_get_404(self):
        assert client.get("/training/jobs/job_nope").status_code == 404

    def test_cancel(self):
        j = client.post(
            "/training/jobs", json={"concern": "draft", "sample": 0.01}
        ).json()
        time.sleep(0.3)
        assert client.post(f"/training/jobs/{j['id']}/cancel").status_code == 200

    def test_cancel_404(self):
        assert client.post("/training/jobs/job_nope/cancel").status_code == 404

    def test_invalid_concern(self):
        assert (
            client.post("/training/jobs", json={"concern": "invalid"}).status_code
            == 422
        )

    def test_dataset_not_found(self):
        assert (
            client.post(
                "/training/jobs", json={"concern": "draft", "dataset_id": "ds_nope"}
            ).status_code
            == 404
        )


class TestModels:
    def test_list(self):
        assert "models" in client.get("/models").json()

    def test_active(self):
        for m in client.get("/models/active").json()["models"]:
            assert m["is_active"] is True

    def test_get_404(self):
        assert client.get("/models/99999").status_code == 404

    def test_activate_404(self):
        assert client.post("/models/99999/activate").status_code == 404

    def test_deactivate_404(self):
        assert client.post("/models/99999/deactivate").status_code == 404

    def test_delete_404(self):
        assert client.delete("/models/99999").status_code == 404
