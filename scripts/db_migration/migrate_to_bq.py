import sqlite3
import pandas as pd
from google.cloud import bigquery
from google.cloud import storage
import os
import sys
from pathlib import Path

# Configuration
DB_PATH = Path("../league_clean.db").resolve()
PROJECT_ID = os.environ.get("SCRIM_PROJECT_ID")
DATASET_ID = "scrimfinder"
LOCATION = "EU"  # Or get from SCRIM_REGION if mapped

if not PROJECT_ID:
    print("Error: SCRIM_PROJECT_ID environment variable not set.")
    sys.exit(1)

client = bigquery.Client(project=PROJECT_ID)
storage_client = storage.Client(project=PROJECT_ID)


def migrate_table(table_name):
    print(f"Migrating table: {table_name}")
    conn = sqlite3.connect(DB_PATH)

    # BigQuery prefers lowercase column names without spaces or hyphens
    # We'll handle this in pandas

    # For large tables, we use chunks
    chunk_size = 500000
    first_chunk = True

    query = f"SELECT * FROM {table_name}"

    # We'll use a GCS bucket for large tables if needed,
    # but let's try direct upload first for tables < 5M rows
    # and for larger ones we'll use load_table_from_dataframe with APPEND

    job_config = bigquery.LoadJobConfig(
        write_disposition="WRITE_TRUNCATE",
    )

    row_count = 0
    for df in pd.read_sql_query(query, conn, chunksize=chunk_size):
        df.columns = [c.lower().replace(" ", "_").replace("-", "_") for c in df.columns]

        if not first_chunk:
            job_config.write_disposition = "WRITE_APPEND"

        job = client.load_table_from_dataframe(
            df, f"{PROJECT_ID}.{DATASET_ID}.{table_name}", job_config=job_config
        )
        job.result()

        row_count += len(df)
        print(f"  Loaded {row_count} rows...")
        first_chunk = False

    conn.close()
    print(f"Successfully migrated {table_name}.")


if __name__ == "__main__":
    # Ensure dataset exists
    dataset_ref = f"{PROJECT_ID}.{DATASET_ID}"
    dataset = bigquery.Dataset(dataset_ref)
    dataset.location = LOCATION
    client.create_dataset(dataset, exists_ok=True)

    tables = [
        "dim_champions",
        "dim_items",
        "dim_runes",
        "dim_players",
        "matches",
        "player_stats",
        "team_stats",
        "bans",
        "player_items",
        "player_runes",
    ]

    for table in tables:
        try:
            migrate_table(table)
        except Exception as e:
            print(f"Error migrating {table}: {e}")
