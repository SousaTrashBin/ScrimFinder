import sqlite3
import threading

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from training_service.routers import games, training

pytestmark = pytest.mark.acceptance


def _client() -> TestClient:
    app = FastAPI()
    app.include_router(games.router)
    app.include_router(training.router)
    return TestClient(app, raise_server_exceptions=False)


def test_openapi_hides_dataset_routes_and_job_dataset_id():
    spec = _client().get("/openapi.json").json()

    assert not any(path.startswith("/datasets") for path in spec["paths"])
    assert "dataset_id" not in spec["components"]["schemas"]["TrainingJobCreate"].get(
        "properties", {}
    )
    assert "dataset_id" not in spec["components"]["schemas"]["TrainingJobResponse"].get(
        "properties", {}
    )


def test_create_training_job_accepts_minimal_payload(monkeypatch):
    stored = {}

    def fake_create_job(
        job_id, concern, algorithm="auto", dataset_id=None, filters=None
    ):
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

    class NoopThread:
        def __init__(self, *args, **kwargs):
            pass

        def start(self):
            pass

    monkeypatch.setattr(training.db, "create_job", fake_create_job)
    monkeypatch.setattr(training.db, "get_job", lambda job_id: stored)
    monkeypatch.setattr(threading, "Thread", NoopThread)

    response = _client().post(
        "/jobs",
        json={"concern": "draft", "algorithm": "auto", "sample": 0.01},
    )

    assert response.status_code == 202
    body = response.json()
    assert body["concern"] == "draft"
    assert body["algorithm"] == "auto"
    assert "dataset_id" not in body


def test_import_league_games_copies_match_participants_items_and_runes(
    tmp_path, monkeypatch
):
    league_db = tmp_path / "league_data.db"
    conn = sqlite3.connect(league_db)
    conn.execute(
        "CREATE TABLE matches (match_id TEXT PRIMARY KEY, match_type TEXT, duration INTEGER, patch TEXT)"
    )
    conn.execute(
        "CREATE TABLE player_stats (match_id TEXT, puuid TEXT, champion_id TEXT, team_id TEXT, win INTEGER)"
    )
    conn.execute(
        "CREATE TABLE player_items (match_id TEXT, puuid TEXT, item_id INTEGER)"
    )
    conn.execute(
        "CREATE TABLE player_runes (match_id TEXT, puuid TEXT, rune_id INTEGER)"
    )
    conn.execute("INSERT INTO matches VALUES ('M1', 'RANKED', 1800, '14.10')")
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
    monkeypatch.setattr(
        games.db, "upsert_dimension_rows", lambda table, rows: len(rows)
    )
    monkeypatch.setattr(games.db, "upsert_league_match", lambda raw: None)

    response = _client().post("/games/import/league", json={"limit": 1})

    assert response.status_code == 200
    assert response.json() == {"imported": 1, "skipped": 0, "errors": []}
    assert inserted["game_id"] == "M1"
    assert inserted["source"] == "league_db"
    assert inserted["raw"]["participants"][0]["items"][0]["item_id"] == 3031
    assert inserted["raw"]["participants"][0]["runes"][0]["rune_id"] == 8005
