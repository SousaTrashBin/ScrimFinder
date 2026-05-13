"""
training_service/tests/test_training.py  —  Integration tests

Requires a live PostgreSQL instance. The CI workflow provides one via the
`services.postgres` block and injects individual PLATFORM_DB_* env vars.

Run locally:
    PLATFORM_DB_HOST=localhost PLATFORM_DB_NAME=testdb \
    PLATFORM_DB_USER=user PLATFORM_DB_PASSWORD=pass \
    pytest training_service/tests/test_training.py -v -m "not slow"
"""

import os
import time

import pytest

from fastapi.testclient import TestClient  # noqa: E402

from training_service.main import app  # noqa: E402
from training_service.core.db import init_db

# ── Skip entire module if no DB is configured ─────────────────────────────────
# Accept either individual vars (CI workflow) or a full DSN string.
_has_db = bool(os.environ.get("PLATFORM_DB_HOST") or os.environ.get("PLATFORM_DB_DSN"))
if not _has_db:
    pytest.skip(
        "No PostgreSQL configured. Set PLATFORM_DB_HOST (or PLATFORM_DB_DSN) to run "
        "training_service integration tests.",
        allow_module_level=True,
    )


init_db()
client = TestClient(app, raise_server_exceptions=True)

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
        assert client.get("/api/v1/training/health").json()["status"] == "ok"

    def test_root(self):
        assert client.get("/api/v1/training/").json()["status"] == "ok"

    def test_docs(self):
        assert client.get("/api/v1/training/docs").status_code == 200


class TestGames:
    def test_ingest(self):
        r = client.post("/api/v1/training/games", json={"data": GAME})
        assert r.status_code == 201
        assert r.json()["id"] == "EUW1_TEST_001"
        assert r.json()["match_type"] == "RANKED"

    def test_ingest_idempotent(self):
        r1 = client.post("/api/v1/training/games", json={"data": GAME})
        r2 = client.post("/api/v1/training/games", json={"data": GAME})
        assert r1.json()["id"] == r2.json()["id"]

    def test_ingest_explicit_id(self):
        r = client.post(
            "/api/v1/training/games", json={"id": "MY_ID", "data": {"x": 1}}
        )
        assert r.json()["id"] == "MY_ID"

    def test_batch(self):
        r = client.post(
            "/api/v1/training/games/batch",
            json={"games": [{"data": GAME}, {"data": GAME2}]},
        )
        assert r.status_code == 201
        assert r.json()["ingested"] == 2

    def test_batch_limit(self):
        r = client.post(
            "/api/v1/training/games/batch",
            json={"games": [{"data": {"matchId": f"G{i}"}} for i in range(1001)]},
        )
        assert r.status_code == 422

    def test_get(self):
        client.post("/api/v1/training/games", json={"data": GAME})
        r = client.get("/api/v1/training/games/EUW1_TEST_001")
        assert r.status_code == 200
        assert r.json()["raw_json"]["matchId"] == "EUW1_TEST_001"

    def test_get_404(self):
        assert client.get("/api/v1/training/games/NOPE_XYZ").status_code == 404

    def test_list(self):
        r = client.get("/api/v1/training/games")
        assert r.status_code == 200
        assert "games" in r.json()
        assert "meta" in r.json()

    def test_delete(self):
        client.post("/api/v1/training/games", json={"id": "DEL_ME", "data": {"x": 1}})
        assert client.delete("/api/v1/training/games/DEL_ME").status_code == 204
        assert client.get("/api/v1/training/games/DEL_ME").status_code == 404

    def test_delete_404(self):
        assert client.delete("/api/v1/training/games/NOPE_XYZ").status_code == 404


class TestFeatures:
    def setup_method(self):
        client.post("/api/v1/training/games", json={"data": GAME})

    def test_extract_by_id(self):
        r = client.post(
            "/api/v1/training/features/extract",
            json={"game_id": "EUW1_TEST_001", "concerns": ["draft"], "store": False},
        )
        assert r.status_code == 200
        assert r.json()["features"][0]["concern"] == "draft"

    def test_extract_inline(self):
        r = client.post(
            "/api/v1/training/features/extract",
            json={"raw_data": GAME, "concerns": ["build"], "store": False},
        )
        assert r.status_code == 200

    def test_extract_404(self):
        assert (
            client.post(
                "/api/v1/training/features/extract",
                json={"game_id": "NOPE", "concerns": ["draft"]},
            ).status_code
            == 404
        )

    def test_extract_422(self):
        assert (
            client.post(
                "/api/v1/training/features/extract", json={"concerns": ["draft"]}
            ).status_code
            == 422
        )

    def test_get_after_store(self):
        client.post(
            "/api/v1/training/features/extract",
            json={"game_id": "EUW1_TEST_001", "concerns": ["draft"], "store": True},
        )
        r = client.get("/api/v1/training/features/EUW1_TEST_001")
        assert r.status_code == 200

    def test_get_404(self):
        assert client.get("/api/v1/training/features/NOPE_XYZ").status_code == 404


class TestTrainingJobs:
    def test_create(self):
        r = client.post(
            "/api/v1/training/jobs", json={"concern": "draft", "sample": 0.01}
        )
        assert r.status_code == 202
        assert r.json()["concern"] == "draft"
        assert r.json()["status"] in ("PENDING", "RUNNING")

    def test_all_concerns(self):
        for c in ["draft", "build", "performance"]:
            assert (
                client.post(
                    "/api/v1/training/jobs", json={"concern": c, "sample": 0.01}
                ).status_code
                == 202
            )

    def test_list(self):
        assert "jobs" in client.get("/api/v1/training/jobs").json()

    def test_get(self):
        j = client.post(
            "/api/v1/training/jobs", json={"concern": "draft", "sample": 0.01}
        ).json()
        r = client.get(f"/api/v1/training/jobs/{j['id']}")
        assert r.status_code == 200
        assert r.json()["id"] == j["id"]

    def test_get_404(self):
        assert client.get("/api/v1/training/jobs/job_nope").status_code == 404

    def test_cancel(self):
        j = client.post(
            "/api/v1/training/jobs", json={"concern": "draft", "sample": 0.01}
        ).json()
        time.sleep(0.3)
        assert client.post(f"/api/v1/training/jobs/{j['id']}/cancel").status_code == 200

    def test_cancel_404(self):
        assert client.post("/api/v1/training/jobs/job_nope/cancel").status_code == 404

    def test_invalid_concern(self):
        assert (
            client.post(
                "/api/v1/training/jobs", json={"concern": "invalid"}
            ).status_code
            == 422
        )


class TestModels:
    def test_list(self):
        assert "models" in client.get("/api/v1/training/models").json()

    def test_active(self):
        for m in client.get("/api/v1/training/models/active").json()["models"]:
            assert m["is_active"] is True

    def test_get_404(self):
        assert client.get("/api/v1/training/models/99999").status_code == 404

    def test_activate_404(self):
        assert client.post("/api/v1/training/models/99999/activate").status_code == 404

    def test_deactivate_404(self):
        assert (
            client.post("/api/v1/training/models/99999/deactivate").status_code == 404
        )

    def test_delete_404(self):
        assert client.delete("/api/v1/training/models/99999").status_code == 404
