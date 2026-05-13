import os
import re
from pathlib import Path

_HERE = Path(__file__).resolve().parent.parent


def _parse_postgres_uri(uri: str) -> dict:
    """Convert postgresql:// URI → psycopg2 kwargs dict."""
    m = re.match(r"postgresql://([^:]+):([^@]+)@([^:]+):(\d+)/([^\?]+)", uri)
    if m:
        user, password, host, port, dbname = m.groups()
        return {
            "host": host,
            "port": int(port),
            "dbname": dbname,
            "user": user,
            "password": password,
        }
    m = re.match(r"postgresql://([^:]+):([^@]+)@([^/]+)/([^\?]+)", uri)
    if m:
        user, password, host, dbname = m.groups()
        return {
            "host": host,
            "port": 5432,
            "dbname": dbname,
            "user": user,
            "password": password,
        }
    raise ValueError(f"Cannot parse PostgreSQL URI: {uri!r}")


class _Config:
    # ── PostgreSQL connection ─────────────────────────────────────────────────
    # Priority: PLATFORM_DB_DSN (URI) > individual PLATFORM_DB_* vars.
    # PLATFORM_DB_KWARGS is what gets unpacked into ThreadedConnectionPool.
    _uri = os.environ.get("PLATFORM_DB_DSN")
    if _uri and _uri.startswith("postgresql://"):
        PLATFORM_DB_KWARGS: dict = _parse_postgres_uri(_uri)
    else:
        PLATFORM_DB_KWARGS: dict = {
            "host": os.environ.get("PLATFORM_DB_HOST", "localhost"),
            "port": int(os.environ.get("PLATFORM_DB_PORT", "5432")),
            "dbname": os.environ.get("PLATFORM_DB_NAME", "platform"),
            "user": os.environ.get("PLATFORM_DB_USER", "postgres"),
            "password": os.environ.get("PLATFORM_DB_PASSWORD", "postgres"),
        }

    # Keep a plain DSN string for logging / compat
    PLATFORM_DB_DSN: str = " ".join(f"{k}={v}" for k, v in PLATFORM_DB_KWARGS.items())

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
