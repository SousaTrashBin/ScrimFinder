"""
jwt_manager/core/config.py

BigQuery-first configuration.
"""

import os
from pathlib import Path
from typing import Optional

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

    # ── Token settings ────────────────────────────────────────────────────
    ACCESS_TOKEN_TTL: int = int(os.environ.get("ACCESS_TOKEN_TTL", "900"))  # 15 min
    REFRESH_TOKEN_TTL: int = int(
        os.environ.get("REFRESH_TOKEN_TTL", "604800")
    )  # 7 days

    # ── Key settings (optional env PEM strings for production) ──────────────
    JWT_PRIVATE_KEY_PEM: Optional[str] = os.environ.get("JWT_PRIVATE_KEY_PEM")
    JWT_PUBLIC_KEY_PEM: Optional[str] = os.environ.get("JWT_PUBLIC_KEY_PEM")
    JWT_KEY_ID: str = os.environ.get("JWT_KEY_ID", "scrimfinder-key-v1")
    ISSUER: str = os.environ.get("JWT_ISSUER", "scrimfinder/jwt-manager")

    # ── Key paths ─────────────────────────────────────────────────────────
    KEYS_DIR: str = os.environ.get("KEYS_DIR", str(_HERE / "keys"))


cfg = _Config()
