import os
from pathlib import Path

_HERE = Path(__file__).resolve().parent.parent


class _Config:
    MODELS_DIR: str = os.environ.get("MODELS_DIR", str(_HERE / "data" / "models"))

    # ── BigQuery ─────────────────────────────────────────────────────────
    BQ_PROJECT: str = os.environ.get("BQ_PROJECT", os.environ.get("SCRIM_PROJECT_ID", ""))
    BQ_DATASET: str = os.environ.get("BQ_DATASET", "scrimfinder")
    BQ_LOCATION: str = os.environ.get("BQ_LOCATION", "EU")
    BQ_PLATFORM_DATASET: str = os.environ.get("BQ_PLATFORM_DATASET", "scrimfinder_platform")

    TRAINING_GRPC_URL: str = os.environ.get("TRAINING_GRPC_URL", "localhost:50051")
    MODEL_RELOAD_INTERVAL: int = int(os.environ.get("MODEL_RELOAD_INTERVAL", 60))

    def ensure_dirs(self):
        for d in [self.MODELS_DIR]:
            Path(d).mkdir(parents=True, exist_ok=True)


cfg = _Config()
