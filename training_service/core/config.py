import os
from pathlib import Path

_HERE = Path(__file__).resolve().parent.parent


class _Config:
    # ── PostgreSQL connection ─────────────────────────────────────────────────
    # Priority: PLATFORM_DB_DSN / ML_DB_DSN > individual PLATFORM_DB_* vars.
    # PLATFORM_DB_KWARGS is what gets unpacked into ThreadedConnectionPool.
    _dsn = os.environ.get("PLATFORM_DB_DSN") or os.environ.get("ML_DB_DSN")
    if _dsn:
        PLATFORM_DB_KWARGS: dict = {"dsn": _dsn}
    else:
        PLATFORM_DB_KWARGS: dict = {
            "host": os.environ.get("PLATFORM_DB_HOST", "localhost"),
            "port": int(os.environ.get("PLATFORM_DB_PORT", "5432")),
            "dbname": os.environ.get("PLATFORM_DB_NAME", "platform"),
            "user": os.environ.get("PLATFORM_DB_USER", "postgres"),
            "password": os.environ.get("PLATFORM_DB_PASSWORD", "postgres"),
        }

    # Keep a plain DSN string for logging / compat
    PLATFORM_DB_DSN: str = _dsn or " ".join(
        f"{k}={v}" for k, v in PLATFORM_DB_KWARGS.items()
    )

    # ── Read-only 78 GB SQLite league dataset ─────────────────────────────────
    LEAGUE_DB: str = os.environ.get(
        "LEAGUE_DB", str(_HERE.parent / "dataset" / "league_data.db")
    )
    LEAGUE_DB_DSN: str = (
        os.environ.get("LEAGUE_DB_DSN") or os.environ.get("ML_DB_DSN") or _dsn or ""
    )
    if not LEAGUE_DB_DSN and os.environ.get("LEAGUE_DB_HOST"):
        LEAGUE_DB_DSN = (
            "host={host} port={port} dbname={db} user={user} password={pw}".format(
                host=os.environ.get("LEAGUE_DB_HOST"),
                port=os.environ.get("LEAGUE_DB_PORT", "5432"),
                db=os.environ.get("LEAGUE_DB_NAME", "ml"),
                user=os.environ.get("LEAGUE_DB_USER", "postgres"),
                pw=os.environ.get(
                    "LEAGUE_DB_PASSWORD",
                    os.environ.get("PLATFORM_DB_PASSWORD", "postgres"),
                ),
            )
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
