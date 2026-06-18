"""
training_service/core/config.py

BigQuery-first configuration. PostgreSQL/SQLite references removed.
"""

import os
from pathlib import Path

_HERE = Path(__file__).resolve().parent.parent


class _Config:
    # ── BigQuery ─────────────────────────────────────────────────────────
    BQ_PROJECT: str = os.environ.get(
        "BQ_PROJECT", os.environ.get("SCRIM_PROJECT_ID", "")
    )
    BQ_LOCATION: str = os.environ.get("BQ_LOCATION", "EU")
    BQ_DATASET: str = os.environ.get("BQ_DATASET", "scrimfinder")
    BQ_PLATFORM_DATASET: str = os.environ.get(
        "BQ_PLATFORM_DATASET", "scrimfinder_platform"
    )

    # ── GCS for model artifacts (if using GCS instead of BigQuery BYTES) ──
    GCS_MODEL_BUCKET: str = os.environ.get("GCS_MODEL_BUCKET", "scrimfinder-models")

    # ── Local paths (for temp files, not DB) ──────────────────────────────
    MODELS_DIR: str = os.environ.get("MODELS_DIR", str(_HERE / "data" / "models"))
    GAMES_DIR: str = os.environ.get("GAMES_DIR", str(_HERE / "data" / "games"))
    DATASETS_DIR: str = os.environ.get("DATASETS_DIR", str(_HERE / "data" / "datasets"))

    MODEL_RELOAD_INTERVAL: int = int(os.environ.get("MODEL_RELOAD_INTERVAL", "600"))

    def ensure_dirs(self):
        for d in [self.MODELS_DIR, self.GAMES_DIR, self.DATASETS_DIR]:
            Path(d).mkdir(parents=True, exist_ok=True)


cfg = _Config()
