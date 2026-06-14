"""
training_service/core/bq_schema.py

BigQuery DDL for ML platform metadata tables.
Run `init_bq_platform(client)` once at startup.
"""

from google.cloud import bigquery

_PLATFORM_TABLES = [
    # games
    """
    CREATE TABLE IF NOT EXISTS `{project}.{platform}.games` (
        id STRING NOT NULL,
        source STRING,
        patch STRING,
        match_type STRING,
        duration_sec INT64,
        platform STRING,
        raw_json JSON,
        ingested_at TIMESTAMP
    )
    """,
    # features
    """
    CREATE TABLE IF NOT EXISTS `{project}.{platform}.features` (
        game_id STRING NOT NULL,
        concern STRING NOT NULL,
        feature_vector JSON,
        feature_names JSON,
        schema_version STRING,
        extracted_at TIMESTAMP
    )
    """,
    # models
    """
    CREATE TABLE IF NOT EXISTS `{project}.{platform}.models` (
        id STRING NOT NULL,
        concern STRING NOT NULL,
        algorithm STRING,
        dataset_id STRING,
        version STRING NOT NULL,
        file_path STRING,
        artifact BYTES,
        metrics JSON,
        hyperparams JSON,
        feature_names JSON,
        is_active BOOL,
        created_at TIMESTAMP,
        activated_at TIMESTAMP
    )
    """,
    # training_jobs
    """
    CREATE TABLE IF NOT EXISTS `{project}.{platform}.training_jobs` (
        id STRING NOT NULL,
        concern STRING NOT NULL,
        algorithm STRING,
        dataset_id STRING,
        status STRING,
        progress INT64,
        stage STRING,
        filters JSON,
        metrics JSON,
        model_id STRING,
        error STRING,
        created_at TIMESTAMP,
        started_at TIMESTAMP,
        completed_at TIMESTAMP
    )
    """,
    # datasets
    """
    CREATE TABLE IF NOT EXISTS `{project}.{platform}.datasets` (
        id STRING NOT NULL,
        name STRING NOT NULL,
        description STRING,
        concern STRING NOT NULL,
        filters JSON,
        game_count INT64,
        row_count INT64,
        status STRING,
        created_at TIMESTAMP,
        built_at TIMESTAMP,
        file_path STRING
    )
    """,
]


def init_bq_platform(client: bigquery.Client, project: str, platform_dataset: str):
    """Create all platform tables if they don't exist."""
    for ddl_template in _PLATFORM_TABLES:
        ddl = ddl_template.format(project=project, platform=platform_dataset)
        job = client.query(ddl)
        job.result()  # wait for completion
    print(f"Platform tables initialized in {project}.{platform_dataset}")
