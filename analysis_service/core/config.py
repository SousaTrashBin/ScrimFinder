import os
from pathlib import Path

_HERE = Path(__file__).resolve().parent.parent

class _Config:
    PLATFORM_DB:  str = os.environ.get("PLATFORM_DB",  str(_HERE / "data" / "platform.db"))
    MODELS_DIR:   str = os.environ.get("MODELS_DIR",   str(_HERE / "data" / "models"))
    LEAGUE_DB:    str = os.environ.get("LEAGUE_DB",    str(_HERE.parent.parent / "dataset" / "league_data.db"))
    MODEL_RELOAD_INTERVAL: int = int(os.environ.get("MODEL_RELOAD_INTERVAL", 60))

    def ensure_dirs(self):
        for d in [self.MODELS_DIR]:
            Path(d).mkdir(parents=True, exist_ok=True)

cfg = _Config()
