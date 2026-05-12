import os
import re
from pathlib import Path

_HERE = Path(__file__).resolve().parent.parent


def _parse_postgres_uri(uri: str) -> dict:
    """Convert postgresql:// URI to connection kwargs for psycopg2."""
    if not uri or not uri.startswith("postgresql://"):
        return {}
    # Handle postgresql://user:pass@host:port/dbname?options
    match = re.match(r"postgresql://([^:]+):([^@]+)@([^:]+):(\d+)/([^\?]+)", uri)
    if not match:
        # Try without port
        match = re.match(r"postgresql://([^:]+):([^@]+)@([^/]+)/([^\?]+)", uri)
        if match:
            user, password, host, dbname = match.groups()
            return {
                "host": host,
                "port": 5432,
                "dbname": dbname,
                "user": user,
                "password": password,
            }
        raise ValueError(f"Cannot parse PostgreSQL URI: {uri}")
    user, password, host, port, dbname = match.groups()
    return {
        "host": host,
        "port": int(port),
        "dbname": dbname,
        "user": user,
        "password": password,
    }


class _Config:
    # Build connection kwargs instead of DSN string
    _uri = os.environ.get("PLATFORM_DB_DSN") or os.environ.get("TEST_PLATFORM_DB_DSN")
    _fallback = {
        "host": os.environ.get("PLATFORM_DB_HOST", "localhost"),
        "port": int(os.environ.get("PLATFORM_DB_PORT", "5432")),
        "dbname": os.environ.get("PLATFORM_DB_NAME", "platform"),
        "user": os.environ.get("PLATFORM_DB_USER", "postgres"),
        "password": os.environ.get("PLATFORM_DB_PASSWORD", "postgres"),
    }
    PLATFORM_DB_KWARGS: dict = _parse_postgres_uri(_uri) if _uri else _fallback

    # Keep DSN for backward compat if anything else needs it
    PLATFORM_DB_DSN: str = " ".join(f"{k}={v}" for k, v in PLATFORM_DB_KWARGS.items())

    LEAGUE_DB: str = os.environ.get(
        "LEAGUE_DB", str(_HERE.parent / "dataset" / "league_data.db")
    )

    MODELS_DIR: str = os.environ.get("MODELS_DIR", str(_HERE / "data" / "models"))
    GAMES_DIR: str = os.environ.get("GAMES_DIR", str(_HERE / "data" / "games"))
    DATASETS_DIR: str = os.environ.get("DATASETS_DIR", str(_HERE / "data" / "datasets"))

    MODEL_RELOAD_INTERVAL: int = int(os.environ.get("MODEL_RELOAD_INTERVAL", 600))

    def ensure_dirs(self):
        for d in [self.MODELS_DIR, self.GAMES_DIR, self.DATASETS_DIR]:
            Path(d).mkdir(parents=True, exist_ok=True)


cfg = _Config()
