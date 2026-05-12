import os
from pathlib import Path

_HERE = Path(__file__).resolve().parent.parent


class _Config:
    # ── PostgreSQL DSN for platform metadata DB ───────────────────────────────
    # Accept full DSN or build from parts — same pattern ops uses for other DBs.
    PLATFORM_DB_DSN: str = os.environ.get(
        "PLATFORM_DB_DSN",
        os.environ.get(
            "TEST_PLATFORM_DB_DSN",
            "host={h} port={p} dbname={db} user={u} password={pw}",
        ),
    )

    # ── Read-only 78 GB SQLite league dataset ─────────────────────────────────
    LEAGUE_DB: str = os.environ.get(
        "LEAGUE_DB", str(_HERE.parent / "dataset" / "league_data.db")
    )

    # ── ML artefact paths ─────────────────────────────────────────────────────
    MODELS_DIR: str = os.environ.get("MODELS_DIR", str(_HERE / "data" / "models"))
    GAMES_DIR: str = os.environ.get("GAMES_DIR", str(_HERE / "data" / "games"))
    DATASETS_DIR: str = os.environ.get("DATASETS_DIR", str(_HERE / "data" / "datasets"))

    MODEL_RELOAD_INTERVAL: int = int(os.environ.get("MODEL_RELOAD_INTERVAL", 600))

    def ensure_dirs(self):
        for d in [self.MODELS_DIR, self.GAMES_DIR, self.DATASETS_DIR]:
            Path(d).mkdir(parents=True, exist_ok=True)


cfg = _Config()
