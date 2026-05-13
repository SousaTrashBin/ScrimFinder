import os


class _Config:
    # ── PostgreSQL (user credentials) ─────────────────────────────────────────
    DB_DSN: str = os.environ.get(
        "JWT_DB_DSN",
        "host={host} port={port} dbname={db} user={user} password={pw}".format(
            host=os.environ.get("JWT_DB_HOST", "localhost"),
            port=os.environ.get("JWT_DB_PORT", "5432"),
            db=os.environ.get("JWT_DB_NAME", "jwt_manager"),
            user=os.environ.get("JWT_DB_USER", "postgres"),
            pw=os.environ.get("JWT_DB_PASSWORD", "postgres"),
        ),
    )

    # ── Redis (active session cache) — DB index 2 ────────────────────────────
    REDIS_URL: str = os.environ.get("REDIS_URL", "redis://localhost:6379/2")

    # ── RS256 key pair ────────────────────────────────────────────────────────
    # Inject PEM strings via K8s secret in prod.
    # If absent, security.py generates a throwaway pair at startup (dev only).
    JWT_PRIVATE_KEY_PEM: str = os.environ.get("JWT_PRIVATE_KEY", "")
    JWT_PUBLIC_KEY_PEM: str = os.environ.get("JWT_PUBLIC_KEY", "")

    # Bump JWT_KEY_ID when rotating keys so Istio re-fetches the JWKS.
    JWT_KEY_ID: str = os.environ.get("JWT_KEY_ID", "scrimfinder-key-v1")

    # ── Token lifetimes ───────────────────────────────────────────────────────
    ACCESS_TOKEN_TTL: int = int(os.environ.get("ACCESS_TOKEN_TTL_SECONDS", 3600))  # 1 h
    REFRESH_TOKEN_TTL: int = int(
        os.environ.get("REFRESH_TOKEN_TTL_SECONDS", 604800)
    )  # 7 d

    ISSUER: str = os.environ.get("JWT_ISSUER", "scrimfinder/jwt-manager")


cfg = _Config()
