"""
jwt_manager/core/db.py

BigQuery implementation for JWT Manager.
"""

import os
import json
import uuid
from datetime import datetime, timezone, date, timedelta
from typing import Optional, Any, List, Dict

from google.cloud import bigquery
from google.cloud.bigquery import QueryJobConfig, ScalarQueryParameter

from .config import cfg

_client: Optional[bigquery.Client] = None


def get_bq_client() -> bigquery.Client:
    global _client
    if _client is None:
        if "BQ_EMULATOR_HOST" in os.environ:
            from google.api_core.client_options import ClientOptions
            from google.auth.credentials import AnonymousCredentials

            _client = bigquery.Client(
                project=cfg.BQ_PROJECT,
                client_options=ClientOptions(
                    api_endpoint=os.environ["BQ_EMULATOR_HOST"]
                ),
                credentials=AnonymousCredentials(),
            )
        else:
            _client = bigquery.Client(project=cfg.BQ_PROJECT)
    return _client


def init_db():
    """Initialize BigQuery tables for JWT Manager."""
    if not cfg.BQ_PROJECT:
        raise RuntimeError("BQ_PROJECT not set")
    client = get_bq_client()

    # Ensure dataset exists
    dataset_id = f"{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}"
    try:
        client.get_dataset(dataset_id)
    except Exception:
        ds = bigquery.Dataset(dataset_id)
        ds.location = cfg.BQ_LOCATION
        client.create_dataset(ds, exists_ok=True)

    tables = [
        f"""
        CREATE TABLE IF NOT EXISTS `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.users` (
            id STRING NOT NULL,
            username STRING NOT NULL,
            email STRING NOT NULL,
            password_hash STRING NOT NULL,
            is_active BOOL DEFAULT TRUE,
            created_at TIMESTAMP
        )
        """,
        f"""
        CREATE TABLE IF NOT EXISTS `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.refresh_tokens` (
            jti STRING NOT NULL,
            user_id STRING NOT NULL,
            revoked BOOL DEFAULT FALSE,
            expires_at TIMESTAMP,
            created_at TIMESTAMP
        )
        """,
        f"""
        CREATE TABLE IF NOT EXISTS `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.access_sessions` (
            jti STRING NOT NULL,
            user_id STRING NOT NULL,
            expires_at TIMESTAMP,
            created_at TIMESTAMP
        )
        """,
    ]
    for ddl in tables:
        job = client.query(ddl)
        job.result()
    print(
        f"JWT Manager tables initialized in {cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}"
    )


def _bq_query(sql: str, params: Optional[List[Any]] = None):
    client = get_bq_client()
    job_config = QueryJobConfig()
    if params:
        job_config.query_parameters = [
            ScalarQueryParameter(f"p{i}", _bq_type(p), p) for i, p in enumerate(params)
        ]
        # Only rewrite if SQL uses %s placeholders (legacy compat)
        if "%s" in sql:
            parts = sql.split("%s")
            sql = (
                "".join(f"{part}@p{i}" for i, part in enumerate(parts[:-1])) + parts[-1]
            )
    location = getattr(cfg, "BQ_LOCATION", None)
    return client.query(sql, job_config=job_config, location=location).result()


def _bq_type(value: Any) -> str:
    if isinstance(value, bool):
        return "BOOL"
    if isinstance(value, int):
        return "INT64"
    if isinstance(value, float):
        return "FLOAT64"
    if isinstance(value, datetime):
        return "TIMESTAMP"
    if isinstance(value, date):
        return "DATE"
    return "STRING"


def _row_to_dict(row: bigquery.Row) -> Dict[str, Any]:
    d = dict(row.items())
    for key, val in d.items():
        if isinstance(val, str) and (val.startswith("{") or val.startswith("[")):
            try:
                d[key] = json.loads(val)
            except json.JSONDecodeError:
                pass
    return d


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


# ── Users ─────────────────────────────────────────────────────────────────────


def create_user(username: str, email: str, password_hash: str) -> Dict[str, Any]:
    user_id = str(uuid.uuid4())
    sql = f"""
    INSERT INTO `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.users`
    (id, username, email, password_hash, is_active, created_at)
    VALUES (@p0, @p1, @p2, @p3, TRUE, CURRENT_TIMESTAMP())
    """
    _bq_query(sql, [user_id, username, email, password_hash])
    return {
        "id": user_id,
        "username": username,
        "email": email,
        "password_hash": password_hash,
        "is_active": True,
    }


def get_user_by_id(user_id: str) -> Optional[Dict[str, Any]]:
    sql = f"SELECT * FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.users` WHERE id = @p0"
    for row in _bq_query(sql, [user_id]):
        return _row_to_dict(row)
    return None


def get_user_by_username(username: str) -> Optional[Dict[str, Any]]:
    sql = f"SELECT * FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.users` WHERE username = @p0"
    for row in _bq_query(sql, [username]):
        return _row_to_dict(row)
    return None


def get_user_by_email(email: str) -> Optional[Dict[str, Any]]:
    sql = f"SELECT * FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.users` WHERE email = @p0"
    for row in _bq_query(sql, [email]):
        return _row_to_dict(row)
    return None


# ── Refresh Tokens ────────────────────────────────────────────────────────────


def store_refresh_token(jti: str, user_id: str, expires_at: datetime):
    sql = f"""
    INSERT INTO `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.refresh_tokens`
    (jti, user_id, revoked, expires_at, created_at)
    VALUES (@p0, @p1, FALSE, @p2, CURRENT_TIMESTAMP())
    """
    _bq_query(sql, [jti, user_id, expires_at])


def get_refresh_token(jti: str) -> Optional[Dict[str, Any]]:
    # Also check DB expiration as defense in depth (JWT exp is checked separately)
    sql = f"""
    SELECT * FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.refresh_tokens`
    WHERE jti = @p0
      AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP())
    """
    for row in _bq_query(sql, [jti]):
        return _row_to_dict(row)
    return None


def revoke_refresh_token(jti: str):
    sql = f"UPDATE `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.refresh_tokens` SET revoked = TRUE WHERE jti = @p0"
    _bq_query(sql, [jti])


def revoke_all_user_tokens(user_id: str):
    sql = f"UPDATE `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.refresh_tokens` SET revoked = TRUE WHERE user_id = @p0"
    _bq_query(sql, [user_id])


# ── Access Sessions ───────────────────────────────────────────────────────────


def cache_access_token(jti: str, user_id: str, ttl_seconds: int):
    expires = datetime.now(timezone.utc) + timedelta(seconds=ttl_seconds)
    sql = f"""
    INSERT INTO `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.access_sessions`
    (jti, user_id, expires_at, created_at)
    VALUES (@p0, @p1, @p2, CURRENT_TIMESTAMP())
    """
    _bq_query(sql, [jti, user_id, expires])


def get_cached_session(jti: str) -> Optional[str]:
    # Only return if the session row exists AND has not expired
    sql = f"""
    SELECT user_id FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.access_sessions`
    WHERE jti = @p0 AND expires_at > CURRENT_TIMESTAMP()
    """
    for row in _bq_query(sql, [jti]):
        return row["user_id"]
    return None


def invalidate_access_token(jti: str):
    sql = f"DELETE FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.access_sessions` WHERE jti = @p0"
    _bq_query(sql, [jti])


def invalidate_all_user_sessions(user_id: str):
    sql = f"DELETE FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.access_sessions` WHERE user_id = @p0"
    _bq_query(sql, [user_id])
