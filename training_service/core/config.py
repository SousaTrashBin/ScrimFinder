import os
import re
from pathlib import Path

_HERE = Path(__file__).resolve().parent.parent


def _parse_postgres_uri(uri: str) -> str:
    """Convert postgresql:// URI to libpq key-value DSN format."""
    if not uri.startswith("postgresql://"):
        return uri  # Already key-value format
    # postgresql://user:pass@host:port/dbname
    match = re.match(r"postgresql://([^:]+):([^@]+)@([^:]+):(\d+)/(.+)", uri)
    if not match:
        raise ValueError(f"Cannot parse PostgreSQL URI: {uri}")
    user, password, host, port, dbname = match.groups()
    return f"host={host} port={port} dbname={dbname} user={user} password={password}"


class _Config:
    PLATFORM_DB_DSN: str = _parse_postgres_uri(
        os.environ.get(
            "PLATFORM_DB_DSN",
            os.environ.get(
                "TEST_PLATFORM_DB_DSN",
                "host={h} port={p} dbname={db} user={u} password={pw}".format(
                    h=os.environ.get("PLATFORM_DB_HOST", "localhost"),
                    p=os.environ.get("PLATFORM_DB_PORT", "5432"),
                    db=os.environ.get("PLATFORM_DB_NAME", "platform"),
                    u=os.environ.get("PLATFORM_DB_USER", "postgres"),
                    pw=os.environ.get("PLATFORM_DB_PASSWORD", "postgres"),
                ),
            ),
        )
    )
    # ... rest unchanged

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
