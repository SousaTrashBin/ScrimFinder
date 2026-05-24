"""
analysis_service/core/db.py

BigQuery implementation for analysis service.
"""

import json
from typing import Optional, Any, List, Dict

from google.cloud import bigquery
from google.cloud.bigquery import QueryJobConfig, ScalarQueryParameter

from analysis_service.core.config import cfg

_client: Optional[bigquery.Client] = None


def get_bq_client() -> bigquery.Client:
    global _client
    if _client is None:
        _client = bigquery.Client(project=cfg.BQ_PROJECT)
    return _client


def _bq_query(sql: str, params: Optional[List[Any]] = None) -> bigquery.table.RowIterator:
    """Execute a query with optional parameters."""
    client = get_bq_client()
    job_config = QueryJobConfig()
    if params:
        job_config.query_parameters = [
            ScalarQueryParameter(f"p{i}", _bq_type(p), p)
            for i, p in enumerate(params)
        ]
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


def _row_to_dict(row: bigquery.Row) -> Dict[str, Any]:
    d = dict(row.items())
    for key, val in d.items():
        if isinstance(val, str) and (val.startswith("{") or val.startswith("[")):
            try:
                d[key] = json.loads(val)
            except json.JSONDecodeError:
                pass
    return d


def query_league(sql: str, params: Optional[List[Any]] = None):
    """Execute query against the league data dataset."""
    league_tables = ["matches", "player_stats", "team_stats", "bans", "player_items", "player_runes",
                     "dim_champions", "dim_items", "dim_runes", "dim_players"]
    for table in league_tables:
        sql = sql.replace(f"FROM {table}", f"FROM `{cfg.BQ_PROJECT}.{cfg.BQ_DATASET}.{table}`")
        sql = sql.replace(f"JOIN {table}", f"JOIN `{cfg.BQ_PROJECT}.{cfg.BQ_DATASET}.{table}`")
    return _bq_query(sql, params)