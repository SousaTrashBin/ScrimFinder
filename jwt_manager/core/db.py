"""
Two storage layers:
  PostgreSQL → persistent user credentials (hashed passwords, account metadata)
  Redis      → ephemeral session store (active JTIs; self-evict on TTL or logout)
"""

import threading
from contextlib import contextmanager

import redis as redis_lib
from psycopg2.pool import ThreadedConnectionPool

from jwt_manager.core.config import cfg

# ── PostgreSQL pool ───────────────────────────────────────────────────────────

_pg_pool: ThreadedConnectionPool | None = None
_pg_lock = threading.Lock()


def _get_pg() -> ThreadedConnectionPool:
    global _pg_pool
    if _pg_pool is not None:
        return _pg_pool
    with _pg_lock:
        if _pg_pool is None:
            _pg_pool = ThreadedConnectionPool(minconn=1, maxconn=10, dsn=cfg.DB_DSN)
    return _pg_pool


@contextmanager
def get_conn():
    pool = _get_pg()
    conn = pool.getconn()
    conn.autocommit = False
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        pool.putconn(conn)


def _row(cur) -> dict | None:
    row = cur.fetchone()
    return dict(zip([d[0] for d in cur.description], row)) if row else None


# ── Redis ─────────────────────────────────────────────────────────────────────

_redis: redis_lib.Redis | None = None
_redis_lock = threading.Lock()


def get_redis() -> redis_lib.Redis:
    global _redis
    if _redis is not None:
        return _redis
    with _redis_lock:
        if _redis is None:
            _redis = redis_lib.from_url(cfg.REDIS_URL, decode_responses=True)
    return _redis


# ── Schema ────────────────────────────────────────────────────────────────────

_SCHEMA = """
CREATE TABLE IF NOT EXISTS users (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    username      TEXT        NOT NULL UNIQUE,
    email         TEXT        NOT NULL UNIQUE,
    password_hash TEXT        NOT NULL,
    is_active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email    ON users(email);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    jti        TEXT        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    issued_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_rt_user_id ON refresh_tokens(user_id);
"""


def init_db():
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(_SCHEMA)


# ── Users ─────────────────────────────────────────────────────────────────────


def create_user(username: str, email: str, password_hash: str) -> dict:
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "INSERT INTO users (username, email, password_hash) VALUES (%s,%s,%s) RETURNING *",
                (username, email, password_hash),
            )
            return _row(cur)


def get_user_by_username(username: str) -> dict | None:
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT * FROM users WHERE username=%s", (username,))
            return _row(cur)


def get_user_by_email(email: str) -> dict | None:
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT * FROM users WHERE email=%s", (email,))
            return _row(cur)


def get_user_by_id(user_id: str) -> dict | None:
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT * FROM users WHERE id=%s", (user_id,))
            return _row(cur)


def deactivate_user(user_id: str):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "UPDATE users SET is_active=FALSE, updated_at=now() WHERE id=%s",
                (user_id,),
            )


# ── Refresh tokens ────────────────────────────────────────────────────────────


def store_refresh_token(jti: str, user_id: str, expires_at):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "INSERT INTO refresh_tokens (jti, user_id, expires_at) VALUES (%s,%s,%s)",
                (jti, user_id, expires_at),
            )


def get_refresh_token(jti: str) -> dict | None:
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT * FROM refresh_tokens WHERE jti=%s", (jti,))
            return _row(cur)


def revoke_refresh_token(jti: str):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("UPDATE refresh_tokens SET revoked=TRUE WHERE jti=%s", (jti,))


def revoke_all_user_tokens(user_id: str):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "UPDATE refresh_tokens SET revoked=TRUE WHERE user_id=%s", (user_id,)
            )


# ── Redis session helpers ─────────────────────────────────────────────────────
# Access-token JTIs stored as  session:{jti} → user_id  with TTL.
# Deleted immediately on logout; expired tokens self-evict.


def cache_access_token(jti: str, user_id: str, ttl: int):
    get_redis().setex(f"session:{jti}", ttl, str(user_id))


def get_cached_session(jti: str) -> str | None:
    return get_redis().get(f"session:{jti}")


def invalidate_access_token(jti: str):
    get_redis().delete(f"session:{jti}")


def invalidate_all_user_sessions(user_id: str):
    """Scan and delete every session key belonging to this user (logout-all)."""
    r = get_redis()
    uid = str(user_id)
    cursor = 0
    while True:
        cursor, keys = r.scan(cursor, match="session:*", count=100)
        for key in keys:
            if r.get(key) == uid:
                r.delete(key)
        if cursor == 0:
            break
