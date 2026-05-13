"""
training_service/tests/test_unit.py  —  Unit tests (no DB required)

All DB and filesystem calls are monkeypatched. These run in CI even when
no PostgreSQL service is present (analysis_service matrix row has no postgres).

Run:
    pytest training_service/tests/test_unit.py -v
"""

import sqlite3
import threading

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from training_service.routers import games, training


def _make_client() -> TestClient:
    """Minimal app without lifespan — no DB connection needed."""
    app = FastAPI()
    app.include_router(games.router)
    app.include_router(training.router)
    return TestClient(app, raise_server_exceptions=False)


# ── OpenAPI schema shape ──────────────────────────────────────────────────────


def test_openapi_hides_dataset_routes_and_job_dataset_id():
    spec = _make_client().get("/openapi.json").json()

    # datasets router is NOT included in the minimal app — no /datasets paths
    assert not any(path.startswith("/datasets") for path in spec["paths"])

    job_create = spec["components"]["schemas"].get("TrainingJobCreate", {})
    job_resp   = spec["components"]["schemas"].get("TrainingJobResponse", {})

    # dataset_id must NOT appear as a required field (it's Optional with default None)
    required_create = job_create.get("required", [])
    required_resp   = job_resp.get("required", [])
    assert "dataset_id" not in required_create
    assert "dataset_id" not in required_resp


# ── Training job creation (mocked DB + thread) ────────────────────────────────


def test_create_training_job_accepts_minimal_payload(monkeypatch):
    stored = {}

    def fake_create_job(job_id, concern, algorithm="auto", dataset_id=None, filters=None):
        stored.update(
            {
                "id": job_id,
                "concern": concern,
                "algorithm": algorithm,
                "dataset_id": dataset_id,
                "filters": filters or {},
                "status": "PENDING",
                "progress": 0,
                "stage": "Queued",
                "metrics": None,
                "model_id": None,
                "error": None,
                "created_at": "2026-05-07T00:00:00+00:00",
                "started_at": None,
                "completed_at": None,
            }
        )

    class _NoopThread:
        def __init__(self, *args, **kwargs):
            pass

        def start(self):
            pass

    monkeypatch.setattr(training.db, "create_job", fake_create_job)
    monkeypatch.setattr(training.db, "get_job",    lambda job_id: stored)
    monkeypatch.setattr(threading,   "Thread",     _NoopThread)

    r = _make_client().post(
        "/jobs",
        json={"concern": "draft", "algorithm": "auto", "sample": 0.01},
    )

    assert r.status_code == 202
    body = r.json()
    assert body["concern"]   == "draft"
    assert body["algorithm"] == "auto"
    # dataset_id should not appear in the response body at all (excluded or None)
    assert body.get("dataset_id") is None


# ── League import (mocked DB + tmp SQLite) ────────────────────────────────────


def test_import_league_games_copies_match_participants_items_and_runes(
    tmp_path, monkeypatch
):
    # Build a minimal league SQLite file
    league_db = tmp_path / "league_data.db"
    conn = sqlite3.connect(league_db)
    conn.execute(
        "CREATE TABLE matches "
        "(match_id TEXT PRIMARY KEY, match_type TEXT, duration INTEGER, patch TEXT)"
    )
    conn.execute(
        "CREATE TABLE player_stats "
        "(match_id TEXT, puuid TEXT, champion_id TEXT, team_id TEXT, win INTEGER)"
    )
    conn.execute("CREATE TABLE player_items  (match_id TEXT, puuid TEXT, item_id INTEGER)")
    conn.execute("CREATE TABLE player_runes  (match_id TEXT, puuid TEXT, rune_id INTEGER)")
    conn.execute("INSERT INTO matches      VALUES ('M1', 'RANKED', 1800, '14.10')")
    conn.execute("INSERT INTO player_stats VALUES ('M1', 'P1', '22', '100', 1)")
    conn.execute("INSERT INTO player_items VALUES ('M1', 'P1', 3031)")
    conn.execute("INSERT INTO player_runes VALUES ('M1', 'P1', 8005)")
    conn.commit()
    conn.close()

    inserted = {}

    monkeypatch.setattr(games.cfg, "LEAGUE_DB", str(league_db))
    monkeypatch.setattr(
        games.db,
        "insert_game",
        lambda game_id, raw, source="manual": inserted.update(
            {"game_id": game_id, "raw": raw, "source": source}
        ),
    )

    r = _make_client().post("/games/import/league", json={"limit": 1})

    assert r.status_code == 200
    assert r.json() == {"imported": 1, "skipped": 0, "errors": []}
    assert inserted["game_id"]                              == "M1"
    assert inserted["source"]                               == "league_db"
    assert inserted["raw"]["participants"][0]["items"][0]["item_id"] == 3031
    assert inserted["raw"]["participants"][0]["runes"][0]["rune_id"] == 8005