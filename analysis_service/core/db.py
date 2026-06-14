"""
analysis_service/core/db.py

BigQuery implementation for analysis service.
"""

import contextlib
import json
import logging
import os
import time
from typing import Any

from google.cloud import bigquery
from google.cloud.bigquery import QueryJobConfig, ScalarQueryParameter

from .config import cfg

_client: bigquery.Client | None = None
logger = logging.getLogger(__name__)


def get_bq_client() -> bigquery.Client:
    global _client
    if _client is not None:
        return _client

    max_retries = 3
    base_delay = 2

    for attempt in range(max_retries):
        try:
            if "BQ_EMULATOR_HOST" in os.environ:
                from google.api_core.client_options import ClientOptions
                from google.auth.credentials import AnonymousCredentials

                client = bigquery.Client(
                    project=cfg.BQ_PROJECT,
                    client_options=ClientOptions(api_endpoint=os.environ["BQ_EMULATOR_HOST"]),
                    credentials=AnonymousCredentials(),
                )
            else:
                client = bigquery.Client(project=cfg.BQ_PROJECT)

            # Verify connection
            list(client.query("SELECT 1").result())

            _client = client
            return _client

        except Exception as e:
            delay = base_delay * (2**attempt)
            logger.warning(f"BQ connection attempt {attempt + 1} failed: {e}. Retrying in {delay}s...")
            if attempt == max_retries - 1:
                logger.error("Failed to connect to BigQuery after all retries.")
                raise RuntimeError(f"Could not establish BigQuery connection: {e}")
            time.sleep(delay)

    raise RuntimeError("Unreachable")


def _bq_query(sql: str, params: list[Any] | None = None) -> bigquery.table.RowIterator:
    """Execute a query with optional parameters."""
    client = get_bq_client()
    job_config = QueryJobConfig()
    if params:
        job_config.query_parameters = [ScalarQueryParameter(f"p{i}", _bq_type(p), p) for i, p in enumerate(params)]
        # Replace %s placeholders with @pN
        parts = sql.split("%s")
        sql = "".join(f"{part}@p{i}" for i, part in enumerate(parts[:-1])) + parts[-1]
    # Pass location to client.query(), NOT to job_config
    location = getattr(cfg, "BQ_LOCATION", None)
    return client.query(sql, job_config=job_config, location=location).result()


def _bq_type(value: Any) -> str:
    if isinstance(value, bool):
        return "BOOL"
    if isinstance(value, int):
        return "INT64"
    if isinstance(value, float):
        return "FLOAT64"
    if isinstance(value, bytes):
        return "BYTES"
    if isinstance(value, str):
        return "STRING"
    return "STRING"


def _row_to_dict(row: bigquery.Row) -> dict[str, Any]:
    d = dict(row.items())
    for key, val in d.items():
        if isinstance(val, str) and (val.startswith("{") or val.startswith("[")):
            with contextlib.suppress(json.JSONDecodeError):
                d[key] = json.loads(val)
    return d


def query_league(sql: str, params: list[Any] | None = None):
    """Execute query against the league data dataset."""
    league_tables = [
        "matches",
        "player_stats",
        "team_stats",
        "bans",
        "player_items",
        "player_runes",
        "dim_champions",
        "dim_items",
        "dim_runes",
        "dim_players",
    ]
    for table in league_tables:
        sql = sql.replace(f"FROM {table}", f"FROM `{cfg.BQ_PROJECT}.{cfg.BQ_DATASET}.{table}`")
        sql = sql.replace(f"JOIN {table}", f"JOIN `{cfg.BQ_PROJECT}.{cfg.BQ_DATASET}.{table}`")
    return _bq_query(sql, params)


# ── Platform metadata (models, jobs, datasets) ───────────────────────────


def get_active_model(concern: str) -> dict[str, Any] | None:
    """Return the active model row for a concern from the platform dataset."""
    sql = f"SELECT * FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.models` WHERE concern = @p0 AND is_active = TRUE"
    for row in _bq_query(sql, [concern]):
        return _row_to_dict(row)
    return None
