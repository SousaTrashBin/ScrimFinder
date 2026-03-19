"""
core/config.py
All environment variable resolution and path configuration.
Import `cfg` anywhere — never read os.environ directly in other modules.
"""
import os
from pathlib import Path

_BASE = Path(__file__).parent.parent  # analysis_service/

class _Config:
    LEAGUE_DB: str = os.environ.get("LEAGUE_DB", str(_BASE.parent / "dataset" / "league_data.db"))
    REGISTRY_DB: str = os.environ.get("REGISTRY_DB", str(_BASE / "data" / "registry.db"))
    MODELS_DIR: str = os.environ.get("MODELS_DIR", str(_BASE / "data" / "models"))
    DATASETS_DIR: str = os.environ.get("DATASETS_DIR", str(_BASE / "data" / "datasets"))
    MODEL_RELOAD_INTERVAL: int = int(os.environ.get("MODEL_RELOAD_INTERVAL", "600"))

    @property
    def registry_db_dir(self) -> str:
        return str(Path(self.REGISTRY_DB).parent)

cfg = _Config()
