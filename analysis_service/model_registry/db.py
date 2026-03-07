"""
model_registry/db.py
SQLite-backed model registry — schema definition and raw CRUD operations.

Schema
------
models
  id            INTEGER   PRIMARY KEY AUTOINCREMENT
  concern       TEXT      NOT NULL  -- 'draft' | 'build' | 'performance'
  version       TEXT      NOT NULL  -- e.g. '2025-W03-1'
  file_path     TEXT      NOT NULL  -- absolute path to the .pkl file on disk
  metrics       TEXT      NOT NULL  -- JSON blob  {"accuracy": 0.81, "f1": 0.79}
  is_active     INTEGER   NOT NULL DEFAULT 0   -- 1 = currently serving
  created_at    TEXT      NOT NULL  -- ISO-8601
  activated_at  TEXT                -- ISO-8601, NULL until promoted
"""

import json
import sqlite3
import os
from datetime import datetime, timezone
from typing import Optional

# Default DB path — can be overridden via env var MODEL_REGISTRY_DB
DEFAULT_DB_PATH = os.environ.get(
    "MODEL_REGISTRY_DB",
    os.path.join(os.path.dirname(__file__), "..", "models", "registry.db"),
)


def _connect(db_path: str = DEFAULT_DB_PATH) -> sqlite3.Connection:
    conn = sqlite3.connect(db_path, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    return conn


def init_db(db_path: str = DEFAULT_DB_PATH) -> None:
    """Create the models table if it doesn't exist."""
    os.makedirs(os.path.dirname(os.path.abspath(db_path)), exist_ok=True)
    with _connect(db_path) as conn:
        conn.execute("""
            CREATE TABLE IF NOT EXISTS models (
                id           INTEGER PRIMARY KEY AUTOINCREMENT,
                concern      TEXT    NOT NULL,
                version      TEXT    NOT NULL,
                file_path    TEXT    NOT NULL,
                metrics      TEXT    NOT NULL DEFAULT '{}',
                is_active    INTEGER NOT NULL DEFAULT 0,
                created_at   TEXT    NOT NULL,
                activated_at TEXT
            )
        """)
        conn.execute("CREATE INDEX IF NOT EXISTS idx_concern_active ON models(concern, is_active)")
        conn.commit()


def register_model(
    concern: str,
    version: str,
    file_path: str,
    metrics: dict,
    db_path: str = DEFAULT_DB_PATH,
) -> int:
    """
    Insert a new model record. Does NOT activate it automatically.
    Returns the new row id.
    """
    with _connect(db_path) as conn:
        cur = conn.execute(
            """
            INSERT INTO models (concern, version, file_path, metrics, is_active, created_at)
            VALUES (?, ?, ?, ?, 0, ?)
            """,
            (concern, version, file_path, json.dumps(metrics), _now()),
        )
        conn.commit()
        return cur.lastrowid


def activate_model(model_id: int, db_path: str = DEFAULT_DB_PATH) -> None:
    """
    Promote a model to active status for its concern.
    Deactivates all other models for the same concern atomically.
    """
    with _connect(db_path) as conn:
        row = conn.execute("SELECT concern FROM models WHERE id = ?", (model_id,)).fetchone()
        if row is None:
            raise ValueError(f"No model with id={model_id}")
        concern = row["concern"]

        # Deactivate all existing active models for this concern
        conn.execute(
            "UPDATE models SET is_active = 0 WHERE concern = ? AND is_active = 1",
            (concern,),
        )
        # Activate the target model
        conn.execute(
            "UPDATE models SET is_active = 1, activated_at = ? WHERE id = ?",
            (_now(), model_id),
        )
        conn.commit()


def get_active_model(concern: str, db_path: str = DEFAULT_DB_PATH) -> Optional[dict]:
    """Return the currently active model record for a concern, or None."""
    with _connect(db_path) as conn:
        row = conn.execute(
            "SELECT * FROM models WHERE concern = ? AND is_active = 1",
            (concern,),
        ).fetchone()
        if row is None:
            return None
        return _row_to_dict(row)


def list_models(concern: Optional[str] = None, db_path: str = DEFAULT_DB_PATH) -> list[dict]:
    """List all model records, optionally filtered by concern."""
    with _connect(db_path) as conn:
        if concern:
            rows = conn.execute(
                "SELECT * FROM models WHERE concern = ? ORDER BY created_at DESC",
                (concern,),
            ).fetchall()
        else:
            rows = conn.execute(
                "SELECT * FROM models ORDER BY concern, created_at DESC"
            ).fetchall()
        return [_row_to_dict(r) for r in rows]


def get_model_by_id(model_id: int, db_path: str = DEFAULT_DB_PATH) -> Optional[dict]:
    with _connect(db_path) as conn:
        row = conn.execute("SELECT * FROM models WHERE id = ?", (model_id,)).fetchone()
        return _row_to_dict(row) if row else None


# ── helpers ──────────────────────────────────────────────────

def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _row_to_dict(row: sqlite3.Row) -> dict:
    d = dict(row)
    d["metrics"] = json.loads(d["metrics"])
    d["is_active"] = bool(d["is_active"])
    return d